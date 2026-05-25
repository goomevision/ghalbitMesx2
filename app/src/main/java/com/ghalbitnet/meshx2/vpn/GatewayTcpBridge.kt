package com.ghalbitnet.meshx2.vpn

import android.content.Context
import android.util.Base64
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.network.MeshSocketClient
import com.ghalbitnet.meshx2.security.KeyStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

object GatewayTcpBridge {

    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 10_000
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class ForwardRequest(
        val clientNodeId: String,
        val sessionId: String,
        val packetId: String,
        val gatewayNodeId: String,
        val sourceAddress: String,
        val sourcePort: Int,
        val destinationAddress: String,
        val destinationPort: Int,
        val tcpPayload: ByteArray,
        val remoteHost: String
    )

    fun handle(
        context: Context,
        request: ForwardRequest
    ): GatewayEgressResult {
        GatewayTcpSessionManager.cleanupIdleSessions()

        val key =
            GatewayTcpSessionManager.sessionKey(
                request.clientNodeId,
                request.sessionId,
                request.sourceAddress,
                request.sourcePort,
                request.destinationAddress,
                request.destinationPort
            )

        var session = GatewayTcpSessionManager.get(key)
        if (session == null) {
            VpnLogManager.info(
                "TCP_BRIDGE_CONNECTING",
                "Menghubungkan ${request.destinationAddress}:${request.destinationPort} untuk ${request.clientNodeId}"
            )
            val socket =
                runCatching {
                    Socket().apply {
                        soTimeout = READ_TIMEOUT_MS
                        connect(
                            InetSocketAddress(
                                request.destinationAddress,
                                request.destinationPort
                            ),
                            CONNECT_TIMEOUT_MS
                        )
                    }
                }.onFailure {
                    VpnLogManager.error(
                        "TCP_BRIDGE_CONNECT_FAILED",
                        "Gagal membuka socket ke ${request.destinationAddress}:${request.destinationPort}",
                        it
                    )
                }.getOrNull()
                    ?: return GatewayEgressResult(
                        success = false,
                        status = MeshForwardProtocol.STATUS_TCP_CONNECT_FAILED,
                        detail = "TCP_BRIDGE_CONNECT_FAILED"
                    )

            session =
                GatewayTcpSessionManager.create(
                    clientNodeId = request.clientNodeId,
                    sessionId = request.sessionId,
                    gatewayNodeId = request.gatewayNodeId,
                    packetId = request.packetId,
                    sourceAddress = request.sourceAddress,
                    sourcePort = request.sourcePort,
                    destinationAddress = request.destinationAddress,
                    destinationPort = request.destinationPort,
                    remoteHost = request.remoteHost,
                    socket = socket
                )
                    ?: run {
                        runCatching { socket.close() }
                        return GatewayEgressResult(
                            success = false,
                            status = MeshForwardProtocol.STATUS_EGRESS_FAILED,
                            detail = "TCP_BRIDGE_MAX_SESSION_REACHED"
                        )
                    }

            VpnLogManager.info(
                "TCP_BRIDGE_SESSION_CREATED",
                "Session ${request.sessionId} dibuat untuk ${request.destinationAddress}:${request.destinationPort}"
            )
            VpnLogManager.info(
                "TCP_BRIDGE_CONNECTED",
                "Socket terkoneksi ke ${request.destinationAddress}:${request.destinationPort}"
            )
            VpnPacketPlaneTestLogger.record(
                "TEST_FLOW_GATEWAY_TCP_CONNECTED",
                TcpFlowDebugSnapshot(
                    flowId = "${request.sessionId}+${request.packetId}",
                    sessionId = request.sessionId,
                    packetId = request.packetId,
                    protocolName = "TCP",
                    ipVersion = 4,
                    packetLength = request.tcpPayload.size,
                    routeMode = MeshForwardMode.MESH_GATEWAY.name,
                    sourceNodeId = request.clientNodeId,
                    gatewayNodeId = request.gatewayNodeId,
                    timestamp = System.currentTimeMillis(),
                    parseStatus = "TCP_GATEWAY_CONNECTED",
                    sourceIp = request.sourceAddress,
                    sourcePort = request.sourcePort,
                    destinationIp = request.destinationAddress,
                    destinationPort = request.destinationPort,
                    tcpState = "ESTABLISHED",
                    decision = "TCP_CONNECTED",
                )
            )
            startReader(session)
        }

        return runCatching {
            session.socket.getOutputStream().write(request.tcpPayload)
            session.socket.getOutputStream().flush()
            GatewayTcpSessionManager.touch(key, request.packetId)
            VpnLogManager.info(
                "TCP_BRIDGE_WRITE_OK",
                "Payload ${request.tcpPayload.size} byte ditulis ke ${request.destinationAddress}:${request.destinationPort}"
            )
            VpnPacketPlaneTestLogger.record(
                "TEST_FLOW_GATEWAY_TCP_WRITE_OK",
                TcpFlowDebugSnapshot(
                    flowId = "${request.sessionId}+${request.packetId}",
                    sessionId = request.sessionId,
                    packetId = request.packetId,
                    protocolName = "TCP",
                    ipVersion = 4,
                    packetLength = request.tcpPayload.size,
                    routeMode = MeshForwardMode.MESH_GATEWAY.name,
                    sourceNodeId = request.clientNodeId,
                    gatewayNodeId = request.gatewayNodeId,
                    timestamp = System.currentTimeMillis(),
                    parseStatus = "TCP_GATEWAY_WRITE",
                    sourceIp = request.sourceAddress,
                    sourcePort = request.sourcePort,
                    destinationIp = request.destinationAddress,
                    destinationPort = request.destinationPort,
                    tcpState = "ESTABLISHED",
                    decision = "TCP_WRITE_OK",
                )
            )
            GatewayEgressResult(
                success = true,
                status = MeshForwardProtocol.STATUS_OK,
                detail = "TCP_BRIDGE_WRITE_OK"
            )
        }.getOrElse {
            GatewayTcpSessionManager.close(key)
            VpnLogManager.error(
                "TCP_BRIDGE_WRITE_FAILED",
                "Gagal menulis payload TCP ke ${request.destinationAddress}:${request.destinationPort}",
                it
            )
            VpnPacketPlaneTestLogger.record(
                "TEST_FLOW_GATEWAY_WRITE_FAILED",
                TcpFlowDebugSnapshot(
                    flowId = "${request.sessionId}+${request.packetId}",
                    sessionId = request.sessionId,
                    packetId = request.packetId,
                    protocolName = "TCP",
                    ipVersion = 4,
                    packetLength = request.tcpPayload.size,
                    routeMode = MeshForwardMode.MESH_GATEWAY.name,
                    sourceNodeId = request.clientNodeId,
                    gatewayNodeId = request.gatewayNodeId,
                    timestamp = System.currentTimeMillis(),
                    parseStatus = "TCP_GATEWAY_WRITE_FAILED",
                    sourceIp = request.sourceAddress,
                    sourcePort = request.sourcePort,
                    destinationIp = request.destinationAddress,
                    destinationPort = request.destinationPort,
                    tcpState = "WRITE_FAILED",
                    decision = "TCP_WRITE_FAILED",
                )
            )
            GatewayEgressResult(
                success = false,
                status = MeshForwardProtocol.STATUS_TCP_WRITE_FAILED,
                detail = "TCP_BRIDGE_WRITE_FAILED"
            )
        }
    }

    private fun startReader(
        session: GatewayTcpSession
    ) {
        scope.launch {
            val key = session.key
            val buffer = ByteArray(8192)
            try {
                val input = session.socket.getInputStream()
                while (isActive && !session.socket.isClosed) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    GatewayTcpSessionManager.touch(key, session.packetId)
                    val payload = buffer.copyOf(read)
                    VpnLogManager.info(
                        "TCP_BRIDGE_READ_RESPONSE",
                        "Membaca ${payload.size} byte dari ${session.destinationAddress}:${session.destinationPort}"
                    )
                    VpnPacketPlaneTestLogger.record(
                        "TEST_FLOW_GATEWAY_TCP_RESPONSE_READ",
                        TcpFlowDebugSnapshot(
                            flowId = "${session.sessionId}+${session.packetId}",
                            sessionId = session.sessionId,
                            packetId = session.packetId,
                            protocolName = "TCP",
                            ipVersion = 4,
                            packetLength = payload.size,
                            routeMode = MeshForwardMode.MESH_GATEWAY.name,
                            sourceNodeId = session.clientNodeId,
                            gatewayNodeId = session.gatewayNodeId,
                            timestamp = System.currentTimeMillis(),
                            parseStatus = "TCP_GATEWAY_RESPONSE_READ",
                            sourceIp = session.sourceAddress,
                            sourcePort = session.sourcePort,
                            destinationIp = session.destinationAddress,
                            destinationPort = session.destinationPort,
                            tcpState = "ESTABLISHED",
                            decision = "TCP_RESPONSE_READ",
                        )
                    )
                    val sent =
                        MeshSocketClient.sendRawBlocking(
                            session.remoteHost,
                            mapOf(
                                "type" to MeshForwardProtocol.TYPE_VPN_RETURN_PACKET,
                                "packetId" to session.packetId,
                                "sourceGatewayId" to session.gatewayNodeId,
                                "destinationClientId" to session.clientNodeId,
                                "sessionId" to session.sessionId,
                                "protocol" to "TCP",
                                "destinationAddress" to session.destinationAddress,
                                "destinationPort" to session.destinationPort,
                                "payloadBase64" to Base64.encodeToString(payload, Base64.NO_WRAP),
                                "timestamp" to System.currentTimeMillis()
                            )
                        )
                    if (sent) {
                        VpnLogManager.info(
                            "TCP_BRIDGE_RETURN_SENT",
                            "Response ${payload.size} byte dikirim balik ke ${session.clientNodeId}"
                        )
                        VpnPacketPlaneTestLogger.record(
                            "TEST_FLOW_RETURN_PACKET_SENT",
                            TcpFlowDebugSnapshot(
                                flowId = "${session.sessionId}+${session.packetId}",
                                sessionId = session.sessionId,
                                packetId = session.packetId,
                                protocolName = "TCP",
                                ipVersion = 4,
                                packetLength = payload.size,
                                routeMode = MeshForwardMode.MESH_GATEWAY.name,
                                sourceNodeId = session.clientNodeId,
                                gatewayNodeId = session.gatewayNodeId,
                                timestamp = System.currentTimeMillis(),
                                parseStatus = "TCP_GATEWAY_RETURN_SENT",
                                sourceIp = session.destinationAddress,
                                sourcePort = session.destinationPort,
                                destinationIp = session.sourceAddress,
                                destinationPort = session.sourcePort,
                                tcpState = "ESTABLISHED",
                                decision = "RETURN_SENT",
                            )
                        )
                    }
                }
            } catch (error: Throwable) {
                VpnLogManager.error(
                    "TCP_BRIDGE_READ_FAILED",
                    "Reader TCP berhenti untuk ${session.destinationAddress}:${session.destinationPort}",
                    error
                )
            } finally {
                GatewayTcpSessionManager.close(key)
            }
        }
    }
}
