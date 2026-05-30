package com.ghalbitnet.meshx2.routing

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.call.CallPeerEndpoint
import com.ghalbitnet.meshx2.core.node.NodeStatusManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object RouteProbeValidator {
    private const val DEFAULT_PORT = 56565
    private const val DEFAULT_TIMEOUT_MS = 1_200

    fun requiresProbe(routeType: String): Boolean {
        return routeType == TriplePathRoutePolicy.LOCAL_MESH_PRIMARY ||
            routeType == TriplePathRoutePolicy.LOCAL_MESH_SECONDARY ||
            routeType == TriplePathRoutePolicy.IDENTITY_COPY_TRACE
    }

    suspend fun probe(
        context: Context,
        routeType: String,
        endpoint: CallPeerEndpoint,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS
    ): RouteProbeResult = withContext(Dispatchers.IO) {
        val host = endpoint.routeHint ?: endpoint.transportIp
        if (!requiresProbe(routeType)) {
            return@withContext RouteProbeResult(
                success = true,
                host = host,
                reason = "probeNotRequired"
            )
        }
        if (host.isNullOrBlank()) {
            Log.w("GHALBIT-ROUTE-PROBE", "failed reason=missingHost routeType=$routeType")
            return@withContext RouteProbeResult(success = false, reason = "missingHost")
        }
        if (DEFAULT_PORT !in 1..65535) {
            Log.w("GHALBIT-ROUTE-PROBE", "failed reason=invalidPort routeType=$routeType")
            return@withContext RouteProbeResult(success = false, host = host, reason = "invalidPort")
        }

        val aliveNodes = NodeStatusManager.getOnlineNodes().filter { it.online }
        val matchedAliveNode =
            aliveNodes.any {
                it.ipAddress == host ||
                    it.name == endpoint.nodeId ||
                    (!endpoint.globalId.isNullOrBlank() && it.name == endpoint.globalId)
            }
        val staleHint = !matchedAliveNode
        val startedAt = System.currentTimeMillis()
        Log.d(
            "GHALBIT-ROUTE-PROBE",
            "start routeType=$routeType host=$host timeoutMs=$timeoutMs alive=${aliveNodes.size} matchedAlive=$matchedAliveNode"
        )

        val socket = Socket()
        return@withContext try {
            socket.connect(InetSocketAddress(host, DEFAULT_PORT), timeoutMs.coerceIn(800, 1_500))
            val latency = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
            Log.d(
                "GHALBIT-ROUTE-PROBE",
                "ok routeType=$routeType host=$host latencyMs=$latency staleHint=$staleHint"
            )
            RouteProbeResult(
                success = true,
                host = host,
                port = DEFAULT_PORT,
                reason = if (staleHint) "connectedButHintWasStale" else "connected",
                latencyMs = latency,
                aliveNodes = aliveNodes.size,
                matchedAliveNode = matchedAliveNode,
                staleHint = staleHint
            )
        } catch (error: Exception) {
            Log.w(
                "GHALBIT-ROUTE-PROBE",
                "failed reason=${error.message ?: "connectFailed"} routeType=$routeType host=$host staleHint=$staleHint"
            )
            RouteProbeResult(
                success = false,
                host = host,
                port = DEFAULT_PORT,
                reason = error.message ?: "connectFailed",
                aliveNodes = aliveNodes.size,
                matchedAliveNode = matchedAliveNode,
                staleHint = true
            )
        } finally {
            runCatching { socket.close() }
        }
    }
}
