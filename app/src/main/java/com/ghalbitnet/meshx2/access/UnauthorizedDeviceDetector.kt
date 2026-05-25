package com.ghalbitnet.meshx2.access

import com.ghalbitnet.meshx2.vpn.VpnLogManager

object UnauthorizedDeviceDetector {

    data class Observation(
        val nodeId: String?,
        val ipAddress: String,
        val detail: String,
        val blocked: Boolean,
        val detectedAt: Long
    )

    private val observations = java.util.concurrent.ConcurrentLinkedDeque<Observation>()

    fun markUnknown(ipAddress: String, detail: String) {
        observations.addFirst(
            Observation(
                nodeId = null,
                ipAddress = ipAddress,
                detail = detail,
                blocked = false,
                detectedAt = System.currentTimeMillis()
            )
        )
        cleanup()
        VpnLogManager.warn(
            "UNKNOWN_DEVICE",
            "Perangkat tanpa HELLO_AUTH diabaikan. ip=$ipAddress detail=$detail"
        )
    }

    fun block(nodeId: String, reason: String) {
        PeerAuthRegistry.markBlocked(nodeId, reason)
        observations.addFirst(
            Observation(
                nodeId = nodeId,
                ipAddress = "",
                detail = reason,
                blocked = true,
                detectedAt = System.currentTimeMillis()
            )
        )
        cleanup()
        VpnLogManager.warn(
            "UNAUTHORIZED_DEVICE_BLOCKED",
            "nodeId=$nodeId reason=$reason"
        )
    }

    fun recentObservations(windowMs: Long = 120_000L): List<Observation> {
        cleanup()
        val now = System.currentTimeMillis()
        return observations.filter { now - it.detectedAt <= windowMs }
    }

    private fun cleanup(windowMs: Long = 10 * 60 * 1000L) {
        val now = System.currentTimeMillis()
        while (true) {
            val last = observations.peekLast() ?: break
            if (now - last.detectedAt <= windowMs) {
                break
            }
            observations.pollLast()
        }
    }
}
