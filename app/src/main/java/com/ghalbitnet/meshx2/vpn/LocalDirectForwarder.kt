package com.ghalbitnet.meshx2.vpn

import android.content.Context

object LocalDirectForwarder {

    fun isReady(): Boolean = false

    fun forward(
        context: Context,
        packet: ByteArray,
        decision: PacketDecisionEngine.PacketDecision
    ): Boolean {
        VpnLogManager.warn(
            "LOCAL_DIRECT_FORWARDER_NOT_READY",
            "${decision.detail} | bytes=${packet.size}"
        )
        return false
    }
}
