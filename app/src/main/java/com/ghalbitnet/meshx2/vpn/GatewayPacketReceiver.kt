package com.ghalbitnet.meshx2.vpn

import android.content.Context
import com.ghalbitnet.meshx2.access.PeerAuthRegistry
import com.ghalbitnet.meshx2.economy.InternetBridgePolicyManager
import com.ghalbitnet.meshx2.network.MeshSocketClient
import org.json.JSONObject

object GatewayPacketReceiver {

    fun handle(
        context: Context,
        payload: JSONObject,
        remoteHost: String
    ) {
        GatewaySessionManager.cleanup(context)
        GatewayNatTable.cleanup(context)

        val sourceNodeId = payload.optString("sourceNodeId").trim()
        val sessionId = payload.optString("sessionId").trim()
        val packetId = payload.optString("packetId").trim()
        val timestamp = payload.optLong("timestamp", 0L)
        val routeMode = payload.optString("routeMode").trim()
        val packetBase64 = payload.optString("packetBase64").trim()
        val remoteMeshHost = remoteHost
        val flowId = "$sessionId+$packetId"
        val protocolName = payload.optString("protocolName", "UNKNOWN")
        val ipVersion = payload.optInt("ipVersion", 0)
        val parseStatus = payload.optString("parseStatus", "UNKNOWN")

        if (!isValidSource(sourceNodeId)) {
            respondFailure(
                remoteHost = remoteMeshHost,
                sourceNodeId = sourceNodeId,
                sessionId = sessionId,
                packetId = packetId,
                status = MeshForwardProtocol.STATUS_INVALID_SOURCE,
                detail = "sourceNodeId tidak sah"
            )
            VpnLogManager.warn("GATEWAY_PACKET_REJECT", "sourceNodeId tidak sah dari $remoteHost")
            return
        }

        if (!PeerAuthRegistry.isAuthorized(sourceNodeId)) {
            respondFailure(
                remoteHost = remoteMeshHost,
                sourceNodeId = sourceNodeId,
                sessionId = sessionId,
                packetId = packetId,
                status = MeshForwardProtocol.STATUS_INVALID_SOURCE,
                detail = "sourceNodeId belum authorized"
            )
            VpnLogManager.warn("UNAUTHORIZED_DEVICE_BLOCKED", "Gateway menolak node tidak authorized: $sourceNodeId")
            return
        }

        val access = InternetBridgePolicyManager.evaluate(context, sourceNodeId)
        if (!access.allowed) {
            respondFailure(
                remoteHost = remoteMeshHost,
                sourceNodeId = sourceNodeId,
                sessionId = sessionId,
                packetId = packetId,
                status = MeshForwardProtocol.STATUS_POLICY_BLOCKED,
                detail = access.detail
            )
            VpnLogManager.warn("GATEWAY_PACKET_REJECT", "policy blocked untuk $sourceNodeId | ${access.detail}")
            return
        }

        when (GatewaySessionManager.validatePacket(context, sourceNodeId, sessionId, packetId, timestamp)) {
            GatewaySessionManager.SessionCheck.DUPLICATE_PACKET -> {
                respondFailure(
                    remoteHost = remoteMeshHost,
                    sourceNodeId = sourceNodeId,
                    sessionId = sessionId,
                    packetId = packetId,
                    status = MeshForwardProtocol.STATUS_DUPLICATE_PACKET,
                    detail = "paket duplikat"
                )
                VpnLogManager.warn("GATEWAY_PACKET_DUPLICATE", "Duplikat dari $sourceNodeId session=$sessionId")
                return
            }
            GatewaySessionManager.SessionCheck.SESSION_EXPIRED -> {
                respondFailure(
                    remoteHost = remoteMeshHost,
                    sourceNodeId = sourceNodeId,
                    sessionId = sessionId,
                    packetId = packetId,
                    status = MeshForwardProtocol.STATUS_SESSION_EXPIRED,
                    detail = "session expired"
                )
                VpnLogManager.warn("GATEWAY_PACKET_SESSION_EXPIRED", "Session expired dari $sourceNodeId")
                return
            }
            GatewaySessionManager.SessionCheck.ACCEPTED -> Unit
        }

        val packetBytes = GatewayEgressHandler.decodePacketBase64(packetBase64)
        VpnPacketPlaneTestLogger.record(
            "TEST_FLOW_GATEWAY_RECEIVED",
            PacketFlowSnapshot(
                flowId = flowId,
                sessionId = sessionId,
                packetId = packetId,
                protocolName = protocolName,
                ipVersion = ipVersion,
                packetLength = packetBytes.size,
                routeMode = routeMode,
                sourceNodeId = sourceNodeId,
                gatewayNodeId = payload.optString("targetGatewayId"),
                timestamp = System.currentTimeMillis(),
                parseStatus = parseStatus,
                sourceIp = payload.optString("sourceIp").takeIf { it.isNotBlank() },
                sourcePort = payload.optInt("sourcePort").takeIf { it > 0 },
                destinationIp = payload.optString("destinationIp").takeIf { it.isNotBlank() },
                destinationPort = payload.optInt("destinationPort").takeIf { it > 0 },
                tcpState = payload.optString("tcpState").takeIf { it.isNotBlank() },
                decision = "GATEWAY_RECEIVED",
            )
        )
        val egress = GatewayEgressHandler.handle(context, sourceNodeId, sessionId, packetBytes, remoteMeshHost)
        if (!egress.success) {
            respondFailure(
                remoteHost = remoteMeshHost,
                sourceNodeId = sourceNodeId,
                sessionId = sessionId,
                packetId = packetId,
                status = egress.status,
                detail = egress.detail
            )
        }

        VpnLogManager.info(
            "GATEWAY_PACKET_HANDLED",
            "source=$sourceNodeId session=$sessionId packet=$packetId mode=$routeMode result=${egress.status}"
        )
    }

    private fun respondFailure(
        remoteHost: String,
        sourceNodeId: String,
        sessionId: String,
        packetId: String,
        status: String,
        detail: String
    ) {
        if (remoteHost.isBlank()) return
        MeshSocketClient.sendRaw(
            remoteHost,
            mapOf(
                "type" to MeshForwardProtocol.TYPE_VPN_FORWARD_RESPONSE,
                "sourceNodeId" to sourceNodeId,
                "sessionId" to sessionId,
                "packetId" to packetId,
                "status" to status,
                "detail" to detail,
                "timestamp" to System.currentTimeMillis()
            )
        )
    }

    private fun isValidSource(sourceNodeId: String): Boolean {
        return sourceNodeId.startsWith("GX-") && sourceNodeId.length >= 8
    }
}
