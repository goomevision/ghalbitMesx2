package com.ghalbitnet.meshx2.routing

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.network.MeshSocketClient
import kotlinx.coroutines.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object RouteDiscovery {
    private lateinit var db: RoutingDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingRequests = ConcurrentHashMap<String, Long>()
    private val routeCache = ConcurrentHashMap<String, RoutingTableEntry>()
    private var appContext: Context? = null
    private var localIp: String = ""
    private var onRouteFoundCallback: ((String, RoutingTableEntry?) -> Unit)? = null

    fun pendingRequestCount(): Int {
        return pendingRequests.size
    }

    fun init(context: Context, myIp: String) {
        db = RoutingDatabase.getInstance(context)
        appContext = context.applicationContext
        localIp = myIp
    }

    suspend fun getBestRoute(destinationIp: String): RoutingTableEntry? {
        // TODO unified identity:
        // route lookup should target canonical globalId and keep destinationIp
        // as transport-level next-hop data only.
        routeCache[destinationIp]?.let { if (System.currentTimeMillis() - it.lastUpdated < 30000) return it }

        val context = appContext
        if (context != null) {
            IntelligentRouteMemory.getHint(context, destinationIp)?.let { hint ->
                val hintedRoute =
                    RoutingTableEntry(
                        destinationIp = destinationIp,
                        nextHopIp = hint.nextHopId,
                        hopCount = hint.hopCount,
                        latencyMs = hint.latencyMs,
                        trustScore = hint.trustScore,
                        lastUpdated = hint.lastSeen
                    )
                routeCache[destinationIp] = hintedRoute
                return hintedRoute
            }
        }

        val directNode =
            MeshRegistry.getNodes()
                .firstOrNull {
                    it.online &&
                        (it.name == destinationIp || it.ipAddress == destinationIp)
                }

        if (directNode != null) {
            val directRoute =
                RoutingTableEntry(
                    destinationIp = directNode.name,
                    nextHopIp = directNode.ipAddress,
                    hopCount = 1,
                    latencyMs = directNode.latency.toLong(),
                    trustScore = directNode.trusted,
                    lastUpdated = System.currentTimeMillis()
                )

            routeCache[destinationIp] =
                directRoute
            context?.let {
                IntelligentRouteMemory.rememberHint(
                    it,
                    RouteHint(
                        destinationId = destinationIp,
                        nextHopId = directNode.ipAddress,
                        latencyMs = directNode.latency.toLong(),
                        hopCount = 1,
                        trustScore = directNode.trusted,
                        lastSeen = System.currentTimeMillis()
                    )
                )
            }

            return directRoute
        }

        val entry = withContext(Dispatchers.IO) { db.routingDao().getRoutes(destinationIp).firstOrNull() }
        entry?.let { routeCache[destinationIp] = it }
        return entry
    }

    fun rememberDirectRoute(
        destinationPeerId: String,
        destinationIp: String,
        latencyMs: Long = 0L,
        trustScore: Int = 50
    ) {
        // TODO unified identity:
        // store canonical route ownership by globalId once discovery and
        // contact resolution stop mixing peerId and IP.
        if (destinationPeerId.isBlank() || destinationIp.isBlank()) {
            return
        }

        val entry =
            RoutingTableEntry(
                destinationIp = destinationPeerId,
                nextHopIp = destinationIp,
                hopCount = 1,
                latencyMs = latencyMs,
                trustScore = trustScore,
                lastUpdated = System.currentTimeMillis()
            )

        routeCache[destinationPeerId] =
            entry
        appContext?.let {
            IntelligentRouteMemory.rememberHint(
                it,
                RouteHint(
                    destinationId = destinationPeerId,
                    nextHopId = destinationIp,
                    latencyMs = latencyMs,
                    hopCount = 1,
                    trustScore = trustScore,
                    lastSeen = System.currentTimeMillis()
                )
            )
        }

        RouteTable.updateRoute(
            destinationPeerId,
            destinationIp,
            1
        )

        scope.launch {
            db.routingDao().insertEntry(entry)
        }
    }

    fun discoverRoute(destinationIp: String, onResult: (RoutingTableEntry?) -> Unit) {
        if (destinationIp == localIp) {
            onResult(null)
            return
        }
        scope.launch {
            val existing = getBestRoute(destinationIp)
            if (existing != null) {
                onResult(existing)
                return@launch
            }
            val requestId = UUID.randomUUID().toString()
            pendingRequests[requestId] = System.currentTimeMillis()
            onRouteFoundCallback = { target, route ->
                if (target == destinationIp) onResult(route)
            }
            val rreq = mapOf(
                "type" to "RREQ",
                "requestId" to requestId,
                "destination" to destinationIp,
                "source" to localIp,
                "hopCount" to 0
            )
            MeshRegistry.getNodes().filter { it.online }.forEach { node ->
                MeshSocketClient.sendRaw(node.ipAddress, rreq)
            }
            delay(3000)
            if (pendingRequests.remove(requestId) != null) onResult(null)
        }
    }

    fun handleRREQ(sourceIp: String, requestId: String, destination: String, hopCount: Int) {
        if (destination == localIp) {
            val rrep = mapOf(
                "type" to "RREP",
                "requestId" to requestId,
                "destination" to localIp,
                "source" to destination,
                "hopCount" to hopCount
            )
            MeshSocketClient.sendRaw(sourceIp, rrep)
            return
        }
        if (hopCount < 5) {
            val nextHop = hopCount + 1
            MeshRegistry.getNodes().filter { it.online && it.ipAddress != sourceIp }.forEach { node ->
                MeshSocketClient.sendRaw(node.ipAddress, mapOf(
                    "type" to "RREQ",
                    "requestId" to requestId,
                    "destination" to destination,
                    "source" to sourceIp,
                    "hopCount" to nextHop
                ))
            }
        }
    }

    fun handleRREP(sourceIp: String, destination: String, hopCount: Int) {
        scope.launch {
            val entry = RoutingTableEntry(
                destinationIp = destination,
                nextHopIp = sourceIp,
                hopCount = hopCount,
                latencyMs = 0,
                trustScore = 50,
                lastUpdated = System.currentTimeMillis()
            )
            db.routingDao().insertEntry(entry)
            routeCache[destination] = entry
            onRouteFoundCallback?.invoke(destination, entry)
        }
    }

    fun clearExpiredRoutes(timeoutMs: Long = 60000) {
        scope.launch {
            val threshold = System.currentTimeMillis() - timeoutMs
            db.routingDao().deleteOlderThan(threshold)
            routeCache.clear()
        }
    }
}
