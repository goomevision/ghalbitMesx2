package com.ghalbitnet.meshx2.vpn

import android.content.Context

object PacketDecisionEngine {

    enum class Action {
        ALLOW_PACKET,
        DROP_PACKET
    }

    data class PacketDecision(
        val action: Action,
        val detail: String,
        val accessDecision: AccessPolicyManager.AccessDecision
    )

    fun decide(
        context: Context,
        localGlobalId: String,
        packet: ByteArray
    ): PacketDecision {
        val mode = VpnOperatingMode.current(context)
        if (mode == VpnOperatingMode.MONITORING_LIGHT) {
            val access =
                AccessPolicyManager.AccessDecision(
                    allowed = true,
                    gatewayAvailable = false,
                    trustHealthy = true,
                    participantValid = true,
                    walletBalance = 0.0,
                    gatewayAddress = "",
                    gatewayId = "",
                    routeScore = 0,
                    userStatus = "MONITORING_LIGHT",
                    gatewayName = "",
                    forwardMode = MeshForwardMode.BLOCKED,
                    detail = "MONITORING_LIGHT"
                )
            return PacketDecision(
                action = Action.ALLOW_PACKET,
                detail = "${describePacket(packet)} | MONITORING_LIGHT",
                accessDecision = access
            )
        }
        val access = AccessPolicyManager.evaluate(context, localGlobalId)
        val packetLabel = describePacket(packet)

        val decision =
            if (mode == VpnOperatingMode.MONITORING_ONLY) {
                PacketDecision(
                    action = Action.ALLOW_PACKET,
                    detail = "$packetLabel | MONITORING_ONLY | ${access.detail}",
                    accessDecision = access
                )
            } else if (!access.allowed) {
                PacketDecision(
                    action = Action.DROP_PACKET,
                    detail = "$packetLabel | ${access.detail}",
                    accessDecision = access
                )
            } else {
                PacketDecision(
                    action = Action.ALLOW_PACKET,
                    detail = "$packetLabel | ${access.detail}",
                    accessDecision = access
                )
            }

        VpnLogManager.packetDecision(decision.action.name, decision.detail)
        return decision
    }

    private fun describePacket(packet: ByteArray): String {
        val version =
            if (packet.isNotEmpty()) ((packet[0].toInt() ushr 4) and 0x0F) else 0
        return when (version) {
            4 -> "IPV4_PACKET len=${packet.size}"
            6 -> "IPV6_PACKET len=${packet.size}"
            else -> "UNKNOWN_PACKET len=${packet.size}"
        }
    }
}
