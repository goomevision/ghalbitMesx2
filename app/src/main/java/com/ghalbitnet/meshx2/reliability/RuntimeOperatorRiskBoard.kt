package com.ghalbitnet.meshx2.reliability

object RuntimeOperatorRiskBoard {

    private fun riskLevel(value: Int): RuntimeOperatorRiskLevel =
        when {
            value >= 80 -> RuntimeOperatorRiskLevel.CRITICAL
            value >= 60 -> RuntimeOperatorRiskLevel.HIGH
            value >= 40 -> RuntimeOperatorRiskLevel.MODERATE
            value >= 20 -> RuntimeOperatorRiskLevel.LOW
            else -> RuntimeOperatorRiskLevel.NONE
        }

    fun report(signals: List<ReliabilitySignalSnapshot>): String {
        val pressure = ReliabilityPressureAggregator.snapshot(signals)
        val freshnessRisk =
            if (ReliabilitySnapshotAggregator.snapshots().any {
                    ReliabilitySnapshotAggregator.freshnessState(it) != SnapshotFreshnessState.FRESH
                }) RuntimeOperatorRiskLevel.MODERATE else RuntimeOperatorRiskLevel.NONE
        val activationRisk =
            when (ReliabilityReadinessGate.status(signals)) {
                ReliabilityReadinessStatus.BLOCKED -> RuntimeOperatorRiskLevel.CRITICAL
                ReliabilityReadinessStatus.GUARDED -> RuntimeOperatorRiskLevel.HIGH
                ReliabilityReadinessStatus.READY -> RuntimeOperatorRiskLevel.LOW
                ReliabilityReadinessStatus.UNKNOWN -> RuntimeOperatorRiskLevel.MODERATE
            }
        return buildString {
            appendLine("OPERATOR RISK BOARD")
            appendLine("======================")
            appendLine("retry storm risk: ${riskLevel(pressure.retryPressureScore).name.lowercase()}")
            appendLine("stale telemetry risk: ${freshnessRisk.name.lowercase()}")
            appendLine("custody backlog risk: ${riskLevel(pressure.relayPressureScore).name.lowercase()}")
            appendLine("delayed sync risk: ${riskLevel(pressure.syncPressureScore).name.lowercase()}")
            appendLine("relay congestion risk: ${riskLevel(pressure.relayPressureScore).name.lowercase()}")
            appendLine("battery/network instability risk: ${RuntimeStressDiagnostics.highestSeverity(signals).name.lowercase()}")
            appendLine("activation risk: ${activationRisk.name.lowercase()}")
        }.trimEnd()
    }
}
