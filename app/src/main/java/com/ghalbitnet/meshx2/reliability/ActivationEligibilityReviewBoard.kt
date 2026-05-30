package com.ghalbitnet.meshx2.reliability

object ActivationEligibilityReviewBoard {

    fun report(context: android.content.Context): String {
        val signals = ReliabilitySignalCollector.collect(context)
        val readiness = ReliabilityReadinessGate.status(signals)
        val confidence = ReliabilitySnapshotAggregator.confidence()
        val operatorRisk =
            when {
                RuntimeOperatorRiskBoard.report(signals).contains("critical") -> "critical"
                RuntimeOperatorRiskBoard.report(signals).contains("high") -> "high"
                RuntimeOperatorRiskBoard.report(signals).contains("moderate") -> "moderate"
                else -> "low"
            }
        val state =
            when (readiness) {
                ReliabilityReadinessStatus.BLOCKED -> "blocked"
                ReliabilityReadinessStatus.UNKNOWN -> "review-required"
                ReliabilityReadinessStatus.GUARDED -> "guarded"
                ReliabilityReadinessStatus.READY -> "eligible-but-disabled"
            }
        val blockers = mutableListOf<String>()
        if (readiness == ReliabilityReadinessStatus.BLOCKED) {
            blockers += "readiness gate blocked"
        }
        if (confidence == ReliabilityObservationConfidence.LOW || confidence == ReliabilityObservationConfidence.UNKNOWN) {
            blockers += "telemetry confidence degraded"
        }
        val reviews = mutableListOf<String>()
        if (readiness == ReliabilityReadinessStatus.UNKNOWN) {
            reviews += "readiness unknown"
        }
        if (operatorRisk == "high" || operatorRisk == "critical") {
            reviews += "operator risk elevated"
        }
        return buildString {
            appendLine("ACTIVATION ELIGIBILITY")
            appendLine("======================")
            appendLine("State: $state")
            appendLine("Activation remains disabled: yes")
            appendLine("Reliability readiness gate: ${readiness.name.lowercase()}")
            appendLine("Telemetry confidence: ${confidence.name.lowercase()}")
            appendLine("Operator risk: $operatorRisk")
            appendLine("Hard blockers: ${if (blockers.isEmpty()) "-" else blockers.joinToString()}")
            appendLine("Review-required reasons: ${if (reviews.isEmpty()) "-" else reviews.joinToString()}")
        }.trimEnd()
    }
}
