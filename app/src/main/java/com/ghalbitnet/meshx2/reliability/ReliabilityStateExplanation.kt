package com.ghalbitnet.meshx2.reliability

object ReliabilityStateExplanation {

    fun explanations(signals: List<ReliabilitySignalSnapshot>): List<ReliabilityExplanation> {
        val pressure = ReliabilityPressureAggregator.snapshot(signals)
        val confidence = ReliabilitySnapshotAggregator.confidence()
        val readiness = ReliabilityReadinessGate.status(signals)
        val results = mutableListOf<ReliabilityExplanation>()

        if (readiness == ReliabilityReadinessStatus.BLOCKED) {
            results += ReliabilityExplanation(
                "why readiness is blocked",
                "Readiness becomes blocked when passive safety signals indicate critical stress or unsafe reliability conditions."
            )
        }

        if (pressure.category != ReliabilityPressureScore.NOMINAL) {
            results += ReliabilityExplanation(
                "why pressure increased",
                "Pressure rises from observational ACK load, retry metadata volume, route-request load, transfer activity, and degraded connectivity scope."
            )
        }

        if (confidence == ReliabilityObservationConfidence.LOW || confidence == ReliabilityObservationConfidence.UNKNOWN) {
            results += ReliabilityExplanation(
                "why confidence degraded",
                "Confidence degrades when more reliability surfaces depend on estimated or placeholder telemetry than observational signals."
            )
        }

        if (readiness == ReliabilityReadinessStatus.GUARDED) {
            results += ReliabilityExplanation(
                "why safe-mode is suggested",
                "Guarded readiness suggests operator caution because passive pressure or stress signals are elevated even though no active orchestration is running."
            )
        }

        if (readiness == ReliabilityReadinessStatus.BLOCKED) {
            results += ReliabilityExplanation(
                "why activation is refused",
                "Activation remains refused because governance and control layers treat blocked readiness as a hard passive stop."
            )
        }

        return results
    }

    fun report(signals: List<ReliabilitySignalSnapshot>): String =
        buildString {
            appendLine("RELIABILITY EXPLAINABILITY")
            appendLine("======================")
            val items = explanations(signals)
            if (items.isEmpty()) {
                appendLine("No additional explanations")
            } else {
                items.forEach {
                    appendLine("${it.title}: ${it.detail}")
                }
            }
        }.trimEnd()
}
