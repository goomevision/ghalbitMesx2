package com.ghalbitnet.meshx2.vpn

import android.content.Context
import android.util.Base64
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.security.KeyStoreManager
import org.json.JSONObject

object ClientReturnPacketHandler {

    fun handle(
        context: Context,
        payload: JSONObject
    ) {
        VpnSessionTable.cleanup()

        val sessionId = payload.optString("sessionId").trim()
        val packetId = payload.optString("packetId").trim()
        val sourceGatewayId = payload.optString("sourceGatewayId").trim()
        val destinationClientId = payload.optString("destinationClientId").trim()
        val protocol = payload.optString("protocol").trim()
        val payloadBase64 = payload.optString("payloadBase64").trim()
        val bytes =
            runCatching {
                Base64.decode(payloadBase64, Base64.NO_WRAP)
            }.getOrElse { ByteArray(0) }

        VpnLogManager.info(
            "VPN_RETURN_PACKET_RECEIVED",
            "session=$sessionId packet=$packetId gateway=$sourceGatewayId protocol=$protocol"
        )
        VpnPacketPlaneTestLogger.record(
            "TEST_FLOW_CLIENT_RETURN_RECEIVED",
            TcpFlowDebugSnapshot(
                flowId = "$sessionId+$packetId",
                sessionId = sessionId,
                packetId = packetId,
                protocolName = protocol.ifBlank { "TCP" },
                ipVersion = 0,
                packetLength = bytes.size,
                routeMode = MeshForwardMode.MESH_GATEWAY.name,
                sourceNodeId = destinationClientId,
                gatewayNodeId = sourceGatewayId,
                timestamp = System.currentTimeMillis(),
                parseStatus = "RETURN_PACKET_RECEIVED",
                tcpState = TcpSessionTracker.get(sessionId)?.connectionState?.name ?: "UNKNOWN",
                decision = "RETURN_RECEIVED",
            )
        )

        val localClientId =
            GlobalMeshIdentityManager.buildGlobalId(KeyStoreManager(context).publicKeyBase64)
        if (destinationClientId.isNotBlank() && destinationClientId != localClientId) {
            VpnLogManager.warn(
                "VPN_RETURN_PACKET_INVALID_DESTINATION",
                "destination=$destinationClientId bukan milik node ini."
            )
            return
        }

        UsageMeter.ensureSession(localClientId, sessionId)
        VpnLogManager.info("USER_IDENTIFIED", "nodeId=$localClientId sessionId=$sessionId")
        UsageMeter.record(
            UsageMeter.UsageRecord(
                nodeId = localClientId,
                sessionId = sessionId,
                packetId = packetId,
                timestamp = System.currentTimeMillis(),
                ipVersion = if (looksLikeIpPacket(bytes)) ((bytes[0].toInt() ushr 4) and 0x0F) else 0,
                protocol = protocol.ifBlank { "TCP" },
                sourceIp = null,
                sourcePort = null,
                destinationIp = null,
                destinationPort = null,
                packetBytes = bytes.size,
                direction = UsageMeter.Direction.DOWNLOAD,
                routeMode = MeshForwardMode.MESH_GATEWAY.name
            )
        )

        when (
            VpnSessionTable.validateReturnPacket(
                sessionId = sessionId,
                packetId = packetId,
                gatewayId = sourceGatewayId,
                payloadSize = bytes.size
            )
        ) {
            VpnSessionTable.ReturnValidation.UNKNOWN_SESSION -> {
                VpnLogManager.warn(
                    "VPN_RETURN_PACKET_UNKNOWN_SESSION",
                    "session=$sessionId belum dikenal"
                )
                return
            }
            VpnSessionTable.ReturnValidation.DUPLICATE_PACKET -> {
                VpnLogManager.warn(
                    "VPN_RETURN_PACKET_DUPLICATE",
                    "packet=$packetId session=$sessionId sudah diproses"
                )
                return
            }
            VpnSessionTable.ReturnValidation.UNTRUSTED_GATEWAY -> {
                VpnLogManager.warn(
                    "VPN_RETURN_PACKET_UNTRUSTED_GATEWAY",
                    "gateway=$sourceGatewayId tidak cocok dengan gateway aktif sesi"
                )
                return
            }
            VpnSessionTable.ReturnValidation.EMPTY_PAYLOAD -> {
                VpnLogManager.warn(
                    "VPN_RETURN_PACKET_EMPTY_PAYLOAD",
                    "payload kosong untuk session=$sessionId"
                )
                return
            }
            VpnSessionTable.ReturnValidation.PAYLOAD_TOO_LARGE -> {
                VpnLogManager.warn(
                    "VPN_RETURN_PACKET_WRITE_FAILED",
                    "payload terlalu besar untuk session=$sessionId"
                )
                return
            }
            VpnSessionTable.ReturnValidation.ACCEPTED -> Unit
        }

        val state = TcpSessionTracker.get(sessionId)
        if (state == null) {
            VpnLogManager.warn(
                "TCP_RETURN_SESSION_NOT_FOUND",
                "session=$sessionId belum punya state TCP translation"
            )
            return
        }

        val finalPacket =
            if (looksLikeIpPacket(bytes)) {
                bytes
            } else {
                VpnLogManager.warn(
                    "VPN_RETURN_PAYLOAD_NEEDS_IP_PACKET_REBUILD",
                    "payload balik masih stream mentah, mencoba rebuild TCP stage 1"
                )
                TcpPacketRebuilder.rebuildReturnPacket(sessionId, bytes)
                    ?: run {
                        VpnPacketPlaneTestLogger.record(
                            "TEST_FLOW_REBUILD_FAILED",
                            TcpFlowDebugSnapshot(
                                flowId = "$sessionId+$packetId",
                                sessionId = sessionId,
                                packetId = packetId,
                                protocolName = protocol.ifBlank { "TCP" },
                                ipVersion = 4,
                                packetLength = bytes.size,
                                routeMode = MeshForwardMode.MESH_GATEWAY.name,
                                sourceNodeId = destinationClientId,
                                gatewayNodeId = sourceGatewayId,
                                timestamp = System.currentTimeMillis(),
                                parseStatus = "TCP_RETURN_REBUILD_FAILED",
                                sourceIp = state.remoteIp,
                                sourcePort = state.remotePort,
                                destinationIp = state.clientIp,
                                destinationPort = state.clientPort,
                                tcpState = state.connectionState.name,
                                decision = "REBUILD_FAILED",
                            )
                        )
                        VpnLogManager.warn(
                            "VPN_RETURN_PACKET_WRITE_FAILED",
                            "rebuild TCP return gagal untuk session=$sessionId"
                        )
                        return
                    }
            }

        if (!VpnTunWriter.isActive()) {
            VpnLogManager.warn(
                "VPN_RETURN_PACKET_WRITE_FAILED",
                "TUN output belum aktif saat packet return datang."
            )
            return
        }

        val written = VpnTunWriter.write(finalPacket)
        if (written) {
            VpnSessionTable.touch(sessionId)
            VpnPacketPlaneTestLogger.record(
                "TEST_FLOW_RETURN_WRITTEN_TO_TUN",
                TcpFlowDebugSnapshot(
                    flowId = "$sessionId+$packetId",
                    sessionId = sessionId,
                    packetId = packetId,
                    protocolName = protocol.ifBlank { "TCP" },
                    ipVersion = 4,
                    packetLength = finalPacket.size,
                    routeMode = MeshForwardMode.MESH_GATEWAY.name,
                    sourceNodeId = destinationClientId,
                    gatewayNodeId = sourceGatewayId,
                    timestamp = System.currentTimeMillis(),
                    parseStatus = "TCP_RETURN_TUN_WRITE",
                    sourceIp = state.remoteIp,
                    sourcePort = state.remotePort,
                    destinationIp = state.clientIp,
                    destinationPort = state.clientPort,
                    tcpState = state.connectionState.name,
                    decision = "TUN_WRITE_OK",
                )
            )
            VpnLogManager.info(
                "TCP_RETURN_WRITTEN_TO_TUN",
                "packet=$packetId session=$sessionId bytes=${finalPacket.size}"
            )
            VpnLogManager.info(
                "VPN_RETURN_PACKET_WRITTEN_TO_TUN",
                "packet=$packetId session=$sessionId bytes=${finalPacket.size}"
            )
        } else {
            VpnLogManager.warn(
                "VPN_RETURN_PACKET_WRITE_FAILED",
                "Gagal menulis packet return ke TUN untuk session=$sessionId"
            )
        }
    }

    private fun looksLikeIpPacket(payload: ByteArray): Boolean {
        if (payload.isEmpty()) return false
        val version = (payload[0].toInt() ushr 4) and 0x0F
        return version == 4 || version == 6
    }
}
