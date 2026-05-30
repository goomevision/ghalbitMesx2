package com.ghalbitnet.meshx2.reliability

object ReliabilityReadinessGate {

    fun status(
        signals: List<ReliabilitySignalSnapshot> = emptyList()
    ): ReliabilityReadinessStatus {
        val pressure = ReliabilityPressureAggregator.snapshot(signals).overallRuntimeHealthScore
        val stress = RuntimeStressDiagnostics.highestSeverity(signals)
        return when {
            stress == RuntimeStressSeverity.CRITICAL -> ReliabilityReadinessStatus.BLOCKED
            pressure >= 60 || stress == RuntimeStressSeverity.HIGH -> ReliabilityReadinessStatus.GUARDED
            pressure >= 0 -> ReliabilityReadinessStatus.READY
            else -> ReliabilityReadinessStatus.UNKNOWN
        }
    }

    fun report(
        signals: List<ReliabilitySignalSnapshot> = emptyList()
    ): String =
        buildString {
            appendLine("RELIABILITY READINESS GATE")
            appendLine("======================")
            appendLine("Status: ${status(signals).name.lowercase()}")
            appendLine("Pressure score: ${ReliabilityPressureAggregator.snapshot(signals).overallRuntimeHealthScore}")
            appendLine("Stress severity: ${RuntimeStressDiagnostics.highestSeverity(signals).name.lowercase()}")
            appendLine("Runtime activation: disabled")
            appendLine("Signal basis: observational/derived")
        }.trimEnd()
}
