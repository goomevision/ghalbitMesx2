package com.ghalbitnet.meshx2.routing

data class RouteCandidateScore(
    val routeType: String,
    val score: Int,
    val routeHint: String?
)

object RouteCandidateRanker {
    fun score(
        routeType: String,
        latencyMs: Long = 0L,
        packetLoss: Int = 0,
        routeStability: Int = 50,
        hopCount: Int = 1,
        batteryCost: Int = 10,
        hasInternet: Boolean = false,
        meshSignal: Int = 0,
        lastSuccessfulBonus: Int = 0,
        emergencyPriority: Boolean = false,
        scoreAdjustment: Int = 0
    ): RouteCandidateScore {
        val internetBonus = if (hasInternet && routeType.contains("INTERNET")) 12 else 0
        val emergencyBonus = if (emergencyPriority) 10 else 0
        val score =
            TriplePathRoutePolicy.baseScore(routeType) +
                internetBonus +
                emergencyBonus +
                (routeStability / 2) +
                (meshSignal / 5) +
                lastSuccessfulBonus -
                (latencyMs / 12L).toInt() -
                packetLoss -
                (hopCount * 8) -
                batteryCost +
                scoreAdjustment
        return RouteCandidateScore(routeType = routeType, score = score, routeHint = null)
    }
}
