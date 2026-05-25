package com.ghalbitnet.meshx2.vpn

import android.content.Context
import android.util.Base64
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.economy.InternetBridgeUsageMonitor
import com.ghalbitnet.meshx2.network.MeshSocketClient
import com.ghalbitnet.meshx2.security.KeyStoreManager

object MeshGatewayForwarder {

    private const val FORWARD_TIMEOUT_MS = 1500

    fun forward(
        context: Context,
        packet: ByteArray,
        decision: PacketDecisionEngine.PacketDecision,
        flowSnapshot: PacketFlowSnapshot
    ): Boolean = forwardRawPacket(context, packet, decision, flowSnapshot)

    fun forwardRawPacket(
        context: Context,
        packet: ByteArray,
        decision: PacketDecisionEngine.PacketDecision,
        flowSnapshot: PacketFlowSnapshot
    ): Boolean {
        val gatewayAddress = decision.accessDecision.gatewayAddress
        if (gatewayAddress.isBlank() || gatewayAddress == "local") {
            VpnLogManager.warn(
                "MESH_GATEWAY_ADDRESS_MISSING",
                "${decision.detail} | gatewayAddress kosong"
            )
            return false
        }

        val sourceNodeId =
            GlobalMeshIdentityManager.buildGlobalId(KeyStoreManager(context).publicKeyBase64)
        val activeSession = InternetBridgeUsageMonitor.activeSessionSnapshot(context)
        val packetId = flowSnapshot.packetId
        val payload: Map<String, Any> =
            mutableMapOf<String, Any>(
                "type" to MeshForwardProtocol.TYPE_VPN_FORWARD_PACKET,
                "sourceNodeId" to sourceNodeId,
                "sessionId" to (activeSession?.sessionId ?: "vpn-$sourceNodeId"),
                "packetId" to packetId,
                "targetGatewayId" to decision.accessDecision.gatewayId,
                "targetGatewayName" to decision.accessDecision.gatewayName,
                "packetLength" to packet.size,
                "packetBase64" to Base64.encodeToString(packet, Base64.NO_WRAP),
                "routeScore" to decision.accessDecision.routeScore,
                "forwardMode" to decision.accessDecision.forwardMode.name,
                "routeMode" to decision.accessDecision.forwardMode.name,
                "protocolName" to flowSnapshot.protocolName,
                "ipVersion" to flowSnapshot.ipVersion,
                "parseStatus" to flowSnapshot.parseStatus,
                "timestamp" to System.currentTimeMillis()
            )
                .apply {
                    flowSnapshot.sourceIp?.let { put("sourceIp", it) }
                    flowSnapshot.sourcePort?.let { put("sourcePort", it) }
                    flowSnapshot.destinationIp?.let { put("destinationIp", it) }
                    flowSnapshot.destinationPort?.let { put("destinationPort", it) }
                    flowSnapshot.tcpState?.let { put("tcpState", it) }
                }

        VpnPacketPlaneTestLogger.record(
            "TEST_FLOW_RAW_PACKET_SENT_TO_MESH",
            flowSnapshot.copy(
                gatewayNodeId = decision.accessDecision.gatewayId,
                decision = "ROUTE_TO_MESH_GATEWAY"
            )
        )

        val delivered =
            MeshSocketClient.sendRawBlocking(
                gatewayAddress,
                payload,
                FORWARD_TIMEOUT_MS
            )

        if (delivered) {
            VpnSessionTable.registerOutgoingSession(
                sessionId = activeSession?.sessionId ?: "vpn-$sourceNodeId",
                gatewayId = decision.accessDecision.gatewayId,
                gatewayName = decision.accessDecision.gatewayName,
                routeMode = decision.accessDecision.forwardMode
            )
            VpnLogManager.info(
                "MESH_GATEWAY_FORWARD",
                "${decision.detail} | gateway=${decision.accessDecision.gatewayName}@$gatewayAddress | bytes=${packet.size}"
            )
            return true
        }

        VpnPacketPlaneTestLogger.record(
            "TEST_FLOW_RAW_PACKET_MESH_SEND_FAILED",
            flowSnapshot.copy(
                gatewayNodeId = decision.accessDecision.gatewayId,
                decision = "MESH_SEND_FAILED"
            )
        )
        VpnLogManager.warn(
            "MESH_GATEWAY_FORWARD_FAILED",
            "${decision.detail} | gateway=${decision.accessDecision.gatewayName}@$gatewayAddress"
        )
        GatewaySelector.markFailed(
            context = context,
            gatewayId = decision.accessDecision.gatewayId,
            gatewayName = decision.accessDecision.gatewayName,
            reason = "gateway mesh tidak menjawab kanal forward"
        )
        return false
    }
}
