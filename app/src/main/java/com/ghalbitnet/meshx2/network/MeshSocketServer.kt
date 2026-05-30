package com.ghalbitnet.meshx2.network

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.economy.ServicePathRecorder
import com.ghalbitnet.meshx2.economy.UsageSessionRecorder
import com.ghalbitnet.meshx2.file.FileTransferManager
import com.ghalbitnet.meshx2.identity.SelfIdentityProtector
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.model.SecurePacket
import com.ghalbitnet.meshx2.routing.RelayEngine
import com.ghalbitnet.meshx2.routing.RouteDiscovery
import com.ghalbitnet.meshx2.routing.IntelligentRouteMemory
import com.ghalbitnet.meshx2.routing.RouteHint
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object MeshSocketServer {
    private const val PORT = 56565
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
    private var packetHandler: ((MeshPacket) -> Unit)? = null
    private var secureHandler: ((SecurePacket) -> Unit)? = null
    private val executor = Executors.newCachedThreadPool()

    fun start(onPacket: (MeshPacket) -> Unit, onSecure: (SecurePacket) -> Unit) {
        packetHandler = onPacket
        secureHandler = onSecure

        if (running) return
        running = true
        Thread {
            try {
                serverSocket = ServerSocket(PORT)
                Log.d("GHALBIT", "Socket server listening on port $PORT")
                while (running) {
                    try {
                        val client = serverSocket?.accept() ?: break
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

                                        handleMeshPacket(p, onPacket, packetOrigin = p.source)
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
                                                onPacket
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
                Log.e("GHALBIT", "MeshSocketServer outer error", e)
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

    private fun handleMeshPacket(
        packet: MeshPacket,
        onPacket: ((MeshPacket) -> Unit)?,
        packetOrigin: String? = null
    ) {
        if (!packetOrigin.isNullOrBlank()) {
            appContext?.let { context ->
                if (packet.source.isNotBlank()) {
                    RouteDiscovery.rememberDirectRoute(packet.source, packetOrigin, trustScore = 60)
                    IntelligentRouteMemory.rememberHint(
                        context,
                        RouteHint(
                            destinationId = packet.source,
                            nextHopId = packetOrigin,
                            latencyMs = 0L,
                            hopCount = 1,
                            trustScore = 60,
                            lastSeen = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
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
            Log.w(
                "GHALBIT",
                "Dropped packet ${packet.type} from ${packet.source}: ${guard.reason}"
            )
            return
        }

        val isBroadcast =
            packet.destination.equals("BROADCAST", ignoreCase = true)

        if (isForLocalNode(packet)) {
            ServicePathRecorder.recordReceive(packet)
            UsageSessionRecorder.recordReceive(packet)
            if (packet.type == ENVELOPE_FILE_CHUNK) {
                val ctx = appContext
                if (ctx != null) {
                    FileTransferManager.handleFileChunk(ctx, packet)
                } else {
                    Log.e("GHALBIT", "No appContext for FILE_CHUNK")
                }
            } else {
                onPacket?.invoke(packet)
            }

            if (isBroadcast) {
                RelayEngine.relayPacket(packet)
            }

            return
        }

        RelayEngine.relayPacket(packet)
    }

    private fun extractPacketPublicKeyHash(packet: MeshPacket): String? {
        val payload = runCatching { JSONObject(packet.payload) }.getOrNull() ?: return null
        val raw = payload.optString("publicKeyHash", null)
        if (!raw.isNullOrBlank()) return raw
        return SelfIdentityProtector.hashPublicKey(payload.optString("sourcePublicKey", null))
    }

    private fun extractPacketDeviceInstanceId(packet: MeshPacket): String? {
        val payload = runCatching { JSONObject(packet.payload) }.getOrNull() ?: return null
        return payload.optString("deviceInstanceId", null)
    }

    private fun isForLocalNode(packet: MeshPacket): Boolean {
        return packet.destination.isBlank() ||
            packet.destination == localPeerId ||
            packet.destination.equals("BROADCAST", ignoreCase = true)
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        executor.shutdown()
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
        }
    }
}
