package com.ghalbitnet.meshx2.reliability

object ReliabilityOperatorSummary {

    fun hints(signals: List<ReliabilitySignalSnapshot>): List<ReliabilityOperatorHints> {
        val pressure = ReliabilityPressureAggregator.snapshot(signals)
        val confidence = ReliabilitySnapshotAggregator.confidence()
        val readiness = ReliabilityReadinessGate.status(signals)
        val items = mutableListOf<ReliabilityOperatorHints>()

        items += if (pressure.category == ReliabilityPressureScore.NOMINAL) {
            ReliabilityOperatorHints("runtime healthy", "Runtime pressure remains nominal", ReliabilityOperatorSeverity.NORMAL)
        } else {
            ReliabilityOperatorHints("elevated pressure", "Runtime pressure category is ${pressure.category.name.lowercase()}", ReliabilityOperatorSeverity.CAUTION)
        }

        if (confidence == ReliabilityObservationConfidence.LOW || confidence == ReliabilityObservationConfidence.UNKNOWN) {
            items += ReliabilityOperatorHints("stale telemetry", "Observation confidence is ${confidence.name.lowercase()}", ReliabilityOperatorSeverity.WARNING)
        }

        if (readiness == ReliabilityReadinessStatus.BLOCKED) {
            items += ReliabilityOperatorHints("blocked activation", "Reliability readiness gate is blocked", ReliabilityOperatorSeverity.CRITICAL)
        }

        if (pressure.relayPressureScore >= 40) {
            items += ReliabilityOperatorHints("custody backlog concern", "Relay pressure score=${pressure.relayPressureScore}", ReliabilityOperatorSeverity.WARNING)
        }

        if (pressure.retryPressureScore >= 40) {
            items += ReliabilityOperatorHints("retry pressure concern", "Retry pressure score=${pressure.retryPressureScore}", ReliabilityOperatorSeverity.WARNING)
        }

        return items
    }

    fun report(signals: List<ReliabilitySignalSnapshot>): String =
        buildString {
            appendLine("OPERATOR SUMMARY")
            appendLine("======================")
            hints(signals).forEach {
                appendLine("${it.title}: ${it.detail} [${it.severity.name.lowercase()}]")
            }
        }.trimEnd()
}
