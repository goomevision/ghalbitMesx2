package com.ghalbitnet.meshx2.economy

import kotlin.math.min

object PeerReputationManager {

    data class Reputation(
        val score: Int,
        val label: String,
        val detail: String
    )

    fun calculate(
        snapshot: PeerServiceSnapshot,
        decisionSummary: InternetBridgeRequestLogManager.PeerDecisionSummary
    ): Reputation {
        val trafficScore =
            min(35.0, snapshot.totalMegaBytes / 8.0)
        val rewardScore =
            min(25.0, (snapshot.totalGatewayReward + snapshot.totalRelayReward) * 2.0)
        val sessionScore =
            min(20.0, snapshot.sessionCount * 2.5)
        val totalChecks =
            decisionSummary.allowed + decisionSummary.denied
        val allowRatio =
            if (totalChecks == 0) 1.0 else decisionSummary.allowed.toDouble() / totalChecks.toDouble()
        val policyScore =
            allowRatio * 20.0

        val raw =
            trafficScore + rewardScore + sessionScore + policyScore
        val score =
            raw.coerceIn(0.0, 100.0).toInt()

        val label =
            when {
                score >= 85 -> "SANGAT KUAT"
                score >= 70 -> "KUAT"
                score >= 50 -> "STABIL"
                score >= 30 -> "PERLU DIPANTAU"
                else -> "LEMAH"
            }

        val detail =
            "Traffic ${"%.1f".format(snapshot.totalMegaBytes)} MB | " +
                "Reward ${"%.2f".format(snapshot.totalGatewayReward + snapshot.totalRelayReward)} GHBT | " +
                "Allow ${decisionSummary.allowed}/${totalChecks}"

        return Reputation(
            score = score,
            label = label,
            detail = detail
        )
    }
}
