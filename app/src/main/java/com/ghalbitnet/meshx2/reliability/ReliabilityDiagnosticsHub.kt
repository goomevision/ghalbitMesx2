package com.ghalbitnet.meshx2.reliability

object ReliabilityDiagnosticsHub {

    private fun summary(
        signals: List<ReliabilitySignalSnapshot>
    ): String {
        val pressure = ReliabilityPressureAggregator.snapshot(signals)
        val stress = RuntimeStressDiagnostics.highestSeverity(signals).name.lowercase()
        return buildString {
            appendLine("RELIABILITY SUMMARY")
            appendLine("======================")
            appendLine("Runtime health: ${pressure.category.name.lowercase()} (${pressure.overallRuntimeHealthScore})")
            appendLine("Retry saturation: ${pressure.retryPressureScore}")
            appendLine("Relay congestion: ${pressure.relayPressureScore}")
            appendLine("Delayed sync pressure: ${pressure.syncPressureScore}")
            appendLine("Expired queue risk: ${pressure.queuePressureScore}")
            appendLine("Custody backlog risk: ${pressure.relayPressureScore}")
            appendLine("Stress severity: $stress")
            appendLine("Signal mix: observational / derived / placeholder")
        }.trimEnd()
    }

    fun report(context: android.content.Context): String =
        buildString {
            val signals =
                ReliabilitySignalCollector.collect(context)
            appendLine("RELIABILITY DIAGNOSTICS")
            appendLine("======================")
            appendLine("Runtime mode: passive-first")
            appendLine("Active retry engine: disabled")
            appendLine("Queue persistence migration: disabled")
            appendLine()
            appendLine(OperationalReviewSnapshotBoard.report(context))
            appendLine()
            appendLine(ReliabilityGovernanceHub.report(context))
            appendLine()
            appendLine(ReliabilityControlPlane.report(context))
            appendLine()
            appendLine(ReliabilityAuditTrail.report(signals))
            appendLine()
            appendLine(ReliabilityOperatorSummary.report(signals))
            appendLine()
            appendLine(ReliabilityStateExplanation.report(signals))
            appendLine()
            appendLine(ReliabilityEscalationPolicy.report(signals))
            appendLine()
            appendLine(ReliabilityReadinessGate.report(signals))
            appendLine()
            appendLine(summary(signals))
            appendLine()
            appendLine(ReliabilitySignalCollector.report(context))
            appendLine()
            appendLine(ReliabilityPressureAggregator.report(signals))
            appendLine()
            appendLine(ReliabilitySnapshotAggregator.report())
            appendLine()
            appendLine(DelayedSyncDiagnostics.report())
            appendLine()
            appendLine(RuntimeStressDiagnostics.report(signals))
            appendLine()
            appendLine(RelayCustodyDiagnostics.report())
            appendLine()
            appendLine(RuntimeQueueObservation.report())
        }.trimEnd()
}
