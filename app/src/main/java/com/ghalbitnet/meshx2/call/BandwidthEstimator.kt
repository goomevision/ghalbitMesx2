package com.ghalbitnet.meshx2.call

data class BandwidthSnapshot(
    val rttMs: Long,
    val packetLossPercent: Int,
    val ackDelayMs: Long,
    val routeStability: Int,
    val estimatedKbps: Int
)

class BandwidthEstimator {
    fun estimate(rttMs: Long, packetLossPercent: Int, ackDelayMs: Long, routeStability: Int): BandwidthSnapshot {
        val stabilityPenalty = (100 - routeStability.coerceIn(0, 100)) / 4
        val delayPenalty = ((rttMs + ackDelayMs) / 25L).toInt()
        val lossPenalty = packetLossPercent.coerceAtLeast(0) * 2
        val estimated = (64 - stabilityPenalty - delayPenalty - lossPenalty).coerceIn(4, 64)
        return BandwidthSnapshot(
            rttMs = rttMs,
            packetLossPercent = packetLossPercent,
            ackDelayMs = ackDelayMs,
            routeStability = routeStability,
            estimatedKbps = estimated
        )
    }
}
