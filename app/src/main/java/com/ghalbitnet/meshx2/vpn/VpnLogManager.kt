package com.ghalbitnet.meshx2.vpn

import com.ghalbitnet.meshx2.core.log.LocalLogBuffer
import com.ghalbitnet.meshx2.core.log.MeshLogger

object VpnLogManager {
    private const val DEBUG_MODE = false
    private val debugPrefixes =
        listOf(
            "TEST_FLOW_",
            "TCP_STATE_",
            "GATEWAY_REJECTED_"
        )

    private fun shouldLog(event: String): Boolean {
        return DEBUG_MODE || debugPrefixes.none { event.startsWith(it) }
    }

    fun info(event: String, message: String) {
        if (!shouldLog(event)) return
        val line = "VPN/$event: $message"
        LocalLogBuffer.add(line)
        MeshLogger.i("VPN", "$event | $message")
    }

    fun warn(event: String, message: String) {
        if (!shouldLog(event)) return
        val line = "VPN/$event: $message"
        LocalLogBuffer.add(line)
        MeshLogger.w("VPN", "$event | $message")
    }

    fun error(event: String, message: String, throwable: Throwable? = null) {
        if (!shouldLog(event)) return
        val line = "VPN/$event: $message"
        LocalLogBuffer.add(line)
        MeshLogger.e("VPN", "$event | $message", throwable)
    }

    fun packetDecision(
        action: String,
        message: String
    ) {
        info("PACKET_DECISION_$action", message)
    }
}
