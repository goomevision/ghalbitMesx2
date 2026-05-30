package com.ghalbitnet.meshx2.identity

import android.content.Context
import com.ghalbitnet.meshx2.economy.EconomyParticipantAggregator
import com.ghalbitnet.meshx2.economy.EconomyParticipantDiagnostics

object SimulationSafetyEvaluator {

    fun evaluate(
        context: Context
    ): SimulationSafetyScore {
        val gate =
            MigrationGateEvaluator.evaluate(context)
        val dedupSummary =
            DedupRiskReporter.summarize(
                IdentityDedupReporter.candidates(context)
            )
        val routingSummary =
            RoutingIdentityAggregator.summarize(
                RoutingShadowIdentityReporter.inspect(context)
            )
        val economySummary =
            EconomyParticipantAggregator.summarize(
                EconomyParticipantDiagnostics.inspect(context)
            )

        var score = 0
        val notes = mutableListOf<String>()

        score += gate.identityAverage.coerceIn(0, 100) / 4
        score += gate.shadowAverage.coerceIn(0, 100) / 4
        score += gate.economyAverage.coerceIn(0, 100) / 5
        score += routingSummary.averageConfidence.coerceIn(0, 100) / 5

        if (gate.rollbackReadiness == "documented") {
            score += 10
        } else {
            notes += "Rollback readiness belum terdokumentasi penuh."
        }

        if (gate.diagnosticsCompleteness == "complete") {
            score += 10
        } else {
            score -= 15
            notes += "Diagnostics completeness belum penuh."
        }

        score -= dedupSummary.unsafeOrAmbiguousCount * 5
        if (dedupSummary.unsafeOrAmbiguousCount > 0) {
            notes += "Masih ada kandidat dedup yang ambigu."
        }

        if (routingSummary.conflictedCount > 0) {
            score -= minOf(20, routingSummary.conflictedCount * 3)
            notes += "Routing identity masih memiliki konflik."
        }

        if (economySummary.unknownCount > 0) {
            score -= minOf(15, economySummary.unknownCount * 2)
            notes += "Economy identity masih punya participant unknown."
        }

        when (gate.category) {
            "ready" -> score += 10
            "guarded" -> score += 0
            "blocked" -> score -= 25
            else -> score -= 15
        }

        val clamped = score.coerceIn(0, 100)
        val label =
            when (clamped) {
                in 80..100 -> "safe"
                in 60..79 -> "guarded"
                in 40..59 -> "unsafe"
                else -> "blocked"
            }

        if (notes.isEmpty()) {
            notes += "Sinyal simulasi terlihat cukup stabil untuk observasi."
        }

        return SimulationSafetyScore(
            score = clamped,
            label = label,
            notes = notes
        )
    }
}
