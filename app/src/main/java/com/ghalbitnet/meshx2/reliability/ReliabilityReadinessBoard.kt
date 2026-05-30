package com.ghalbitnet.meshx2.reliability

object ReliabilityReadinessBoard {

    fun report(context: android.content.Context): String {
        val signals = ReliabilitySignalCollector.collect(context)
        val pressure = ReliabilityPressureAggregator.snapshot(signals)
        val stress = RuntimeStressDiagnostics.highestSeverity(signals)
        val confidence = ReliabilitySnapshotAggregator.confidence()
        val freshness =
            ReliabilitySnapshotAggregator.snapshots()
                .map { ReliabilitySnapshotAggregator.freshnessState(it) }
                .maxByOrNull { it.ordinal }
                ?: SnapshotFreshnessState.FRESH
        val readiness = ReliabilityReadinessGate.status(signals)
        val operatorRisk =
            when (pressure.category) {
                ReliabilityPressureScore.CRITICAL,
                ReliabilityPressureScore.OVERLOADED -> "high"
                ReliabilityPressureScore.STRESSED -> "moderate"
                ReliabilityPressureScore.ELEVATED -> "low"
                ReliabilityPressureScore.NOMINAL -> "none"
            }
        return buildString {
            appendLine("RELIABILITY READINESS BOARD")
            appendLine("======================")
            appendLine("Runtime diagnostics: passive")
            appendLine("Governance diagnostics: active visibility")
            appendLine("Activation eligibility: ${readiness.name.lowercase()}")
            appendLine("Operator risk: $operatorRisk")
            appendLine("Observation confidence: ${confidence.name.lowercase()}")
            appendLine("Telemetry freshness: ${freshness.name.lowercase()}")
            appendLine("Pressure level: ${pressure.category.name.lowercase()}")
            appendLine("Stress level: ${stress.name.lowercase()}")
        }.trimEnd()
    }
}
