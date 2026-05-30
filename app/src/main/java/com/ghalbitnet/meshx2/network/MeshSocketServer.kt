package com.ghalbitnet.meshx2.network

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.economy.ServicePathRecorder
import com.ghalbitnet.meshx2.economy.UsageSessionRecorder
import com.ghalbitnet.meshx2.file.FileTransferManager
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.model.SecurePacket
import com.ghalbitnet.meshx2.routing.RelayEngine
import com.ghalbitnet.meshx2.routing.RouteDiscovery
import com.ghalbitnet.meshx2.vpn.ClientReturnPacketHandler
import com.ghalbitnet.meshx2.vpn.GatewayPacketReceiver
import com.ghalbitnet.meshx2.vpn.MeshForwardProtocol
import com.ghalbitnet.meshx2.vpn.VpnLogManager
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object MeshSocketServer {
    private const val PORT = 56565
    private const val MAX_RAW_PACKET_LENGTH = 160 * 1024
    private const val RESTART_COOLDOWN_MS = 5_000L
    private const val ENVELOPE_MESH_PACKET = "MESH_PACKET"
    private const val ENVELOPE_SECURE_PACKET = "SECURE_PACKET"
    private const val ENVELOPE_BLOCK_PROPOSAL = "BLOCK_PROPOSAL"
    private const val ENVELOPE_RREQ = "RREQ"
    private const val ENVELOPE_RREP = "RREP"
    private const val ENVELOPE_FILE_CHUNK = "FILE_CHUNK"
    private const val ENVELOPE_VPN_FORWARD_PACKET = MeshForwardProtocol.TYPE_VPN_FORWARD_PACKET
    private const val ENVELOPE_VPN_FORWARD_RESPONSE = MeshForwardProtocol.TYPE_VPN_FORWARD_RESPONSE
    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    var onBlock: ((JSONObject) -> Unit)? = null
    var appContext: Context? = null
    var localPeerId: String = ""
    private var packetHandler: ((MeshPacket) -> Unit)? = null
    private var secureHandler: ((SecurePacket) -> Unit)? = null
    @Volatile
    private var executor: ExecutorService? = null
    @Volatile
    private var lastRestartAt = 0L

    fun start(onPacket: (MeshPacket) -> Unit, onSecure: (SecurePacket) -> Unit) {
        packetHandler = onPacket
        secureHandler = onSecure

        Log.d("GHALBIT-TCP-LISTENER", "startRequested port=$PORT")
        if (running.get()) {
            Log.d("GHALBIT-TCP-LISTENER", "alreadyRunning port=$PORT closed=${serverSocket?.isClosed}")
            return
        }
        if (executor == null || executor?.isShutdown == true || executor?.isTerminated == true) {
            executor = Executors.newCachedThreadPool()
            VpnLogManager.info("MESH_SERVER_EXECUTOR_CREATED", "Executor baru dibuat untuk MeshSocketServer.")
        }
        running.set(true)
        VpnLogManager.info("MESH_SERVER_START", "MeshSocketServer start pada port $PORT.")
        Thread {
            try {
                serverSocket = ServerSocket(PORT)
                Log.d("GHALBIT-TCP-LISTENER", "bindSuccess port=$PORT")
                Log.d("GHALBIT-TCP-LISTENER", "acceptLoopStarted port=$PORT")
                Log.d("GHALBIT", "Socket server listening on port $PORT")
                while (running.get()) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        Log.d("GHALBIT-TCP-LISTENER", "accepted remote=${client.inetAddress?.hostAddress}:${client.port}")
                        val activeExecutor = executor
                        if (!running.get() || activeExecutor == null || activeExecutor.isShutdown || activeExecutor.isTerminated) {
                            try { client.close() } catch (_: Exception) {}
                            VpnLogManager.warn(
                                "MESH_SERVER_EXECUTOR_REJECTED",
                                "Executor tidak aktif saat ada client baru; accept loop dihentikan aman."
                            )
                            break
                        }
                        activeExecutor.execute {
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

                                        handleMeshPacket(p, onPacket)
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
                                    ENVELOPE_VPN_FORWARD_PACKET -> {
                                        val ctx = appContext
                                        if (ctx != null) {
                                            GatewayPacketReceiver.handle(
                                                context = ctx,
                                                payload = json,
                                                remoteHost = client.inetAddress?.hostAddress.orEmpty()
                                            )
                                        } else {
                                            Log.e("GHALBIT", "VPN forward packet dropped: appContext null")
                                        }
                                    }
                                    ENVELOPE_VPN_FORWARD_RESPONSE -> {
                                        Log.d(
                                            "GHALBIT",
                                            "VPN forward response ${json.optString("status")} for packet ${json.optString("packetId")}: ${json.optString("detail")}"
                                        )
                                    }
                                    MeshForwardProtocol.TYPE_VPN_RETURN_PACKET -> {
                                        Log.d(
                                            "GHALBIT",
                                            "VPN return packet for session ${json.optString("sessionId")} from ${json.optString("destinationAddress")}:${json.optInt("destinationPort")}"
                                        )
                                        val ctx = appContext
                                        if (ctx != null) {
                                            ClientReturnPacketHandler.handle(ctx, json)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                if (running.get()) Log.e("GHALBIT", "MeshSocketServer inner error", e)
                            } finally {
                                try { client.close() } catch (_: Exception) {}
                            }
                        }
                    } catch (e: RejectedExecutionException) {
                        VpnLogManager.warn(
                            "MESH_SERVER_EXECUTOR_REJECTED",
                            "Executor menolak task baru; server dihentikan aman."
                        )
                        break
                    } catch (e: Exception) {
                        if (running.get()) Log.e("GHALBIT", "MeshSocketServer accept loop error", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("GHALBIT", "MeshSocketServer outer error", e)
                Log.e("GHALBIT-TCP-LISTENER", "bindFail port=$PORT error=${e.message}", e)
            } finally {
                running.set(false)
                Log.d("GHALBIT-TCP-LISTENER", "socketClosed reason=acceptLoopStopped port=$PORT")
                VpnLogManager.info("MESH_SERVER_ACCEPT_LOOP_STOPPED", "Accept loop MeshSocketServer berhenti.")
            }
        }.start()
    }

    fun isRunning(): Boolean {
        return running.get() && serverSocket?.isClosed == false
    }

    fun ensureRunning(reason: String) {
        if (isRunning()) {
            return
        }
        Log.w("GHALBIT-TCP-LISTENER", "notRunningOnHealthCheck reason=$reason running=${running.get()} closed=${serverSocket?.isClosed}")
        restart(reason)
    }

    @Synchronized
    fun restart(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastRestartAt < RESTART_COOLDOWN_MS) {
            Log.d("GHALBIT-TCP-LISTENER", "restartSkipped reason=$reason cooldownMs=${RESTART_COOLDOWN_MS - (now - lastRestartAt)}")
            return
        }
        lastRestartAt = now

        val onPacket = packetHandler
        val onSecure = secureHandler
        if (onPacket == null || onSecure == null) {
            Log.w("GHALBIT-TCP-LISTENER", "restartSkipped reason=$reason handlerMissing=${onPacket == null || onSecure == null}")
            return
        }

        Log.w("GHALBIT-TCP-LISTENER", "restart reason=$reason")
        stop()
        start(onPacket, onSecure)
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

                    handleMeshPacket(packet, packetHandler)
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
                        packetHandler
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("GHALBIT", "Injected packet error from $sourceIp", e)
        }
    }

    private fun handleMeshPacket(
        packet: MeshPacket,
        onPacket: ((MeshPacket) -> Unit)?
    ) {
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

    private fun isForLocalNode(packet: MeshPacket): Boolean {
        return packet.destination.isBlank() ||
            packet.destination == localPeerId ||
            packet.destination.equals("BROADCAST", ignoreCase = true)
    }

    fun stop() {
        Log.d("GHALBIT-TCP-LISTENER", "stopRequested port=$PORT")
        VpnLogManager.info("MESH_SERVER_STOP", "MeshSocketServer stop diminta.")
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        val activeExecutor = executor
        executor = null
        activeExecutor?.shutdown()
        try {
            if (activeExecutor != null && !activeExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                activeExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            activeExecutor?.shutdownNow()
        }
    }
}
