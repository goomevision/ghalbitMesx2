package com.ghalbitnet.meshx2.future.ai

/**
 * AI ROUTING ENGINE
 *
 * Tahap sekarang:
 * - placeholder aman
 *
 * Masa depan:
 * - memilih jalur terbaik
 * - belajar dari latency
 * - belajar dari node gagal
 * - memilih gateway internet terbaik
 */
object AiRoutingEngine {

    fun scoreRoute(
        latencyMs: Long,
        trust: Int,
        batteryLevel: Int,
        hopCount: Int
    ): Double {

        val latencyScore = 100.0 - latencyMs.coerceAtMost(1000) / 10.0
        val trustScore = trust.toDouble()
        val batteryScore = batteryLevel.toDouble()
        val hopPenalty = hopCount * 10.0

        return latencyScore + trustScore + batteryScore - hopPenalty
    }

    fun chooseBestNode(
        nodes: List<RouteCandidate>
    ): RouteCandidate? {

        return nodes.maxByOrNull {
            scoreRoute(
                it.latencyMs,
                it.trust,
                it.batteryLevel,
                it.hopCount
            )
        }
    }
}

data class RouteCandidate(
    val peerId: String,
    val ipAddress: String,
    val latencyMs: Long,
    val trust: Int,
    val batteryLevel: Int,
    val hopCount: Int
)
