package com.ghalbitnet.meshx2.network

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.economy.ServicePathRecorder
import com.ghalbitnet.meshx2.economy.UsageSessionRecorder
import com.ghalbitnet.meshx2.file.FileTransferManager
import com.ghalbitnet.meshx2.identity.SelfIdentityProtector
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.model.SecurePacket
import com.ghalbitnet.meshx2.routing.IntelligentRouteMemory
import com.ghalbitnet.meshx2.routing.RelayEngine
import com.ghalbitnet.meshx2.routing.RouteDiscovery
import com.ghalbitnet.meshx2.routing.RouteHint
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object MeshSocketServer {
    private const val TAG_TCP = "GHALBIT-TCP-LISTENER"
    private const val TAG_ROUTE = "GHALBIT-ROUTE-DEST"
    private const val PORT = 56565
    private const val RESTART_COOLDOWN_MS = 5_000L
    private const val MAX_RAW_PACKET_LENGTH = 160 * 1024
    private const val ENVELOPE_MESH_PACKET = "MESH_PACKET"
    private const val ENVELOPE_SECURE_PACKET = "SECURE_PACKET"
    private const val ENVELOPE_BLOCK_PROPOSAL = "BLOCK_PROPOSAL"
    private const val ENVELOPE_RREQ = "RREQ"
    private const val ENVELOPE_RREP = "RREP"
    private const val ENVELOPE_FILE_CHUNK = "FILE_CHUNK"
    private var serverSocket: ServerSocket? = null
    @Volatile private var running = false
    var onBlock: ((JSONObject) -> Unit)? = null
    var appContext: Context? = null
    var localPeerId: String = ""
    var localPublicKeyHash: String? = null
    var localDeviceInstanceId: String? = null
    var localGlobalId: String? = null
    private var packetHandler: ((MeshPacket) -> Unit)? = null
    private var secureHandler: ((SecurePacket) -> Unit)? = null
    @Volatile private var lastRestartAtMs = 0L
    private val lock = Any()
    private var executor = Executors.newCachedThreadPool()

    private fun ensureExecutorActive() {
        if (executor.isShutdown || executor.isTerminated) {
            executor = Executors.newCachedThreadPool()
        }
    }

    fun start(onPacket: (MeshPacket) -> Unit, onSecure: (SecurePacket) -> Unit) {
        packetHandler = onPacket
        secureHandler = onSecure
        synchronized(lock) {
            Log.d(TAG_TCP, "startRequested port=$PORT")
            if (running) {
                Log.d(TAG_TCP, "alreadyRunning")
                return
            }
            ensureExecutorActive()
            running = true
        }
        Thread {
            try {
                serverSocket = ServerSocket(PORT)
                Log.d(TAG_TCP, "bindSuccess port=$PORT")
                Log.d(TAG_TCP, "acceptLoopStarted")
                Log.d("GHALBIT", "Socket server listening on port $PORT")
                while (running) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        val remoteIp = client.inetAddress?.hostAddress.orEmpty()
                        Log.d(TAG_TCP, "accepted remote=$remoteIp:${client.port}")
                        executor.execute {
                            try {
                                client.soTimeout = 5000
                                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                                val jsonStr = reader.readLine() ?: return@execute
                                if (jsonStr.length > MAX_RAW_PACKET_LENGTH) {
                                    Log.w("GHALBIT", "Dropped oversized raw packet")
                                    return@execute
                                }
                                Log.d("GHALBIT", "Raw received: $jsonStr")
                                val json = JSONObject(jsonStr)
                                when (json.optString("type")) {
                                    ENVELOPE_MESH_PACKET -> {
                                        val p = MeshPacket(
                                            packetId = json.getString("packetId"),
                                            source = json.getString("source"),
                                            destination = json.getString("destination"),
                                            type = json.optString("packetType", "DATA"),
                                            payload = json.getString("payload"),
                                            hopCount = json.optInt("hopCount"),
                                            maxHop = json.optInt("maxHop"),
                                            timestamp = json.optLong("timestamp"),
                                            encrypted = json.optBoolean("encrypted")
                                        )
                                        handleMeshPacket(p, onPacket, packetOrigin = remoteIp.ifBlank { p.source })
                                    }
                                    ENVELOPE_SECURE_PACKET -> {
                                        val s = SecurePacket(
                                            sourcePublicKey = json.getString("sourcePublicKey"),
                                            destinationPublicKey = json.getString("destinationPublicKey"),
                                            encryptedPayload = json.getString("encryptedPayload"),
                                            packetId = json.getString("packetId"),
                                            hopCount = json.optInt("hopCount"),
                                            maxHop = json.optInt("maxHop"),
                                            timestamp = json.optLong("timestamp")
                                        )
                                        onSecure(s)
                                    }
                                    ENVELOPE_BLOCK_PROPOSAL -> {
                                        Log.d("GHALBIT", "Block proposal received")
                                        onBlock?.invoke(json.getJSONObject("block"))
                                    }
                                    ENVELOPE_RREQ -> {
                                        RouteDiscovery.handleRREQ(
                                            json.getString("source"),
                                            json.getString("requestId"),
                                            json.getString("destination"),
                                            json.getInt("hopCount")
                                        )
                                    }
                                    ENVELOPE_RREP -> {
                                        RouteDiscovery.handleRREP(
                                            json.getString("source"),
                                            json.getString("destination"),
                                            json.getInt("hopCount")
                                        )
                                    }
                                    ENVELOPE_FILE_CHUNK -> {
                                        try {
                                            handleMeshPacket(
                                                MeshPacket(
                                                    packetId = json.optString("packetId", ""),
                                                    source = json.optString("source", ""),
                                                    destination = json.optString("destination", ""),
                                                    type = "FILE_CHUNK",
                                                    payload = json.getString("payload"),
                                                    hopCount = json.optInt("hopCount"),
                                                    maxHop = json.optInt("maxHop"),
                                                    timestamp = json.optLong("timestamp"),
                                                    encrypted = false
                                                ),
                                                onPacket,
                                                packetOrigin = remoteIp
                                            )
                                        } catch (e: Exception) {
                                            Log.e("GHALBIT", "FILE_CHUNK error: ${e.message}")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                if (running) Log.e("GHALBIT", "MeshSocketServer inner error", e)
                            } finally {
                                try { client.close() } catch (_: Exception) {}
                            }
                        }
                    } catch (e: Exception) {
                        if (running) Log.e("GHALBIT", "MeshSocketServer accept loop error", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG_TCP, "bindFail port=$PORT", e)
                Log.e("GHALBIT", "MeshSocketServer outer error", e)
            } finally {
                Log.d(TAG_TCP, "socketClosed")
                running = false
            }
        }.start()
    }

    fun injectPacket(jsonStr: String, sourceIp: String) {
        try {
            if (jsonStr.length > MAX_RAW_PACKET_LENGTH) {
                Log.w("GHALBIT", "Dropped oversized injected packet from $sourceIp")
                return
            }
            val json = JSONObject(jsonStr)
            when (json.optString("type")) {
                ENVELOPE_MESH_PACKET -> {
                    val packet = MeshPacket(
                        packetId = json.optString("packetId", ""),
                        source = json.optString("source", sourceIp),
                        destination = json.optString("destination", ""),
                        type = json.optString("packetType", "DATA"),
                        payload = json.optString("payload", ""),
                        hopCount = json.optInt("hopCount"),
                        maxHop = json.optInt("maxHop"),
                        timestamp = json.optLong("timestamp"),
                        encrypted = json.optBoolean("encrypted")
                    )
                    handleMeshPacket(packet, packetHandler, packetOrigin = sourceIp)
                }
                ENVELOPE_SECURE_PACKET -> {
                    val secure = SecurePacket(
                        sourcePublicKey = json.getString("sourcePublicKey"),
                        destinationPublicKey = json.getString("destinationPublicKey"),
                        encryptedPayload = json.getString("encryptedPayload"),
                        packetId = json.getString("packetId"),
                        hopCount = json.optInt("hopCount"),
                        maxHop = json.optInt("maxHop"),
                        timestamp = json.optLong("timestamp")
                    )
                    secureHandler?.invoke(secure)
                }
                ENVELOPE_FILE_CHUNK -> {
                    handleMeshPacket(
                        MeshPacket(
                            packetId = json.optString("packetId", ""),
                            source = json.optString("source", sourceIp),
                            destination = json.optString("destination", ""),
                            type = ENVELOPE_FILE_CHUNK,
                            payload = json.getString("payload"),
                            hopCount = json.optInt("hopCount"),
                            maxHop = json.optInt("maxHop"),
                            timestamp = json.optLong("timestamp"),
                            encrypted = false
                        ),
                        packetHandler,
                        packetOrigin = sourceIp
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("GHALBIT", "Injected packet error from $sourceIp", e)
        }
    }

    private fun handleMeshPacket(packet: MeshPacket, onPacket: ((MeshPacket) -> Unit)?, packetOrigin: String? = null) {
        rememberInboundRoute(packet, packetOrigin)
        val selfDecision = SelfIdentityProtector.evaluate(
            packet = packet,
            packetOrigin = packetOrigin,
            packetPublicKeyHash = extractPacketPublicKeyHash(packet),
            packetDeviceInstanceId = extractPacketDeviceInstanceId(packet),
            identityContext = SelfIdentityProtector.IdentityContext(
                selfNodeId = localPeerId,
                selfPublicKeyHash = localPublicKeyHash,
                selfDeviceInstanceId = localDeviceInstanceId
            )
        )
        if (selfDecision.selfLoop) {
            SelfIdentityProtector.logSelfLoop(packet, selfDecision.reason)
            return
        }

        val guard = MeshTrafficGuard.validatePacket(packet)
        if (!guard.allowed) {
            Log.w("GHALBIT", "Dropped packet ${packet.type} from ${packet.source}: ${guard.reason}")
            return
        }

        val isBroadcast = packet.destination.equals("BROADCAST", ignoreCase = true)
        if (isForLocalNode(packet)) {
            Log.d(TAG_ROUTE, "deliverLocal type=${packet.type} destination=${packet.destination} localPeer=$localPeerId localHash=${localPublicKeyHash ?: "-"}")
            ServicePathRecorder.recordReceive(packet)
            UsageSessionRecorder.recordReceive(packet)
            if (packet.type == ENVELOPE_FILE_CHUNK) {
                val ctx = appContext
                if (ctx != null) FileTransferManager.handleFileChunk(ctx, packet) else Log.e("GHALBIT", "No appContext for FILE_CHUNK")
            } else {
                onPacket?.invoke(packet)
            }
            if (isBroadcast) RelayEngine.relayPacket(packet)
            return
        }

        Log.d(TAG_ROUTE, "relayOnly type=${packet.type} destination=${packet.destination} localPeer=$localPeerId localHash=${localPublicKeyHash ?: "-"}")
        RelayEngine.relayPacket(packet)
    }

    private fun rememberInboundRoute(packet: MeshPacket, packetOrigin: String?) {
        if (packetOrigin.isNullOrBlank()) return
        appContext?.let { context ->
            val identifiers = buildSet {
                if (packet.source.isNotBlank()) add(packet.source)
                extractPayloadString(packet, "sourceNodeId")?.let { add(it) }
                extractPayloadString(packet, "sourceGlobalId")?.let { add(it) }
                extractPayloadString(packet, "publicKeyHash")?.let { add(it) }
                extractPayloadString(packet, "sourcePublicKeyHash")?.let { add(it) }
            }
            identifiers.forEach { destinationId ->
                RouteDiscovery.rememberDirectRoute(destinationId, packetOrigin, trustScore = 70)
                IntelligentRouteMemory.rememberHint(
                    context,
                    RouteHint(
                        destinationId = destinationId,
                        nextHopId = packetOrigin,
                        latencyMs = 0L,
                        hopCount = 1,
                        trustScore = 70,
                        lastSeen = System.currentTimeMillis()
                    )
                )
            }
            if (identifiers.isNotEmpty()) {
                Log.d(TAG_ROUTE, "rememberInbound ids=${identifiers.joinToString(",")} via=$packetOrigin")
            }
        }
    }

    private fun extractPacketPublicKeyHash(packet: MeshPacket): String? {
        val payload = runCatching { JSONObject(packet.payload) }.getOrNull() ?: return null
        val raw = payload.optString("publicKeyHash", null)
        if (!raw.isNullOrBlank()) return raw
        val sourceRaw = payload.optString("sourcePublicKeyHash", null)
        if (!sourceRaw.isNullOrBlank()) return sourceRaw
        return SelfIdentityProtector.hashPublicKey(payload.optString("sourcePublicKey", null))
    }

    private fun extractPacketDeviceInstanceId(packet: MeshPacket): String? =
        extractPayloadString(packet, "deviceInstanceId") ?: extractPayloadString(packet, "sourceDeviceInstanceId")

    private fun extractPayloadString(packet: MeshPacket, key: String): String? {
        val payload = runCatching { JSONObject(packet.payload) }.getOrNull() ?: return null
        return payload.optString(key).takeIf { it.isNotBlank() }
    }

    private fun isForLocalNode(packet: MeshPacket): Boolean {
        val destinations = buildSet {
            packet.destination.takeIf { it.isNotBlank() }?.let { add(it) }
            extractPayloadString(packet, "targetNodeId")?.let { add(it) }
            extractPayloadString(packet, "targetGlobalId")?.let { add(it) }
            extractPayloadString(packet, "destinationGlobalId")?.let { add(it) }
            extractPayloadString(packet, "targetPublicKeyHash")?.let { add(it) }
            extractPayloadString(packet, "destinationPublicKeyHash")?.let { add(it) }
            extractPayloadString(packet, "targetDeviceInstanceId")?.let { add(it) }
            extractPayloadString(packet, "destinationDeviceInstanceId")?.let { add(it) }
        }
        if (destinations.isEmpty()) return true
        if (destinations.any { it.equals("BROADCAST", ignoreCase = true) }) return true
        val localIds = buildSet {
            localPeerId.takeIf { it.isNotBlank() }?.let { add(it) }
            localGlobalId?.takeIf { it.isNotBlank() }?.let { add(it) }
            localPublicKeyHash?.takeIf { it.isNotBlank() }?.let { add(it) }
            localDeviceInstanceId?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        val matched = destinations.any { destination -> localIds.any { local -> destination == local } }
        if (!matched) {
            Log.d(TAG_ROUTE, "destinationMismatch destinations=${destinations.joinToString(",")} localIds=${localIds.joinToString(",")}")
        }
        return matched
    }

    fun stop() {
        Log.d(TAG_TCP, "stopRequested")
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        executor.shutdown()
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow()
        } catch (e: InterruptedException) {
            executor.shutdownNow()
        }
    }

    fun isRunning(): Boolean {
        val socketOpen = serverSocket?.isClosed == false
        return running && socketOpen
    }

    fun ensureRunning(reason: String) {
        if (!isRunning()) {
            Log.w(TAG_TCP, "notRunningOnHealthCheck reason=$reason")
            restart(reason)
        }
    }

    fun restart(reason: String) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            if (now - lastRestartAtMs < RESTART_COOLDOWN_MS) {
                Log.d(TAG_TCP, "restart skipped cooldown reason=$reason")
                return
            }
            if (packetHandler == null || secureHandler == null) {
                Log.w(TAG_TCP, "restart skipped missingHandlers reason=$reason")
                return
            }
            lastRestartAtMs = now
        }
        Log.w(TAG_TCP, "restart reason=$reason")
        stop()
        val onPacket = packetHandler ?: return
        val onSecure = secureHandler ?: return
        start(onPacket, onSecure)
    }
}
