package com.ghalbitnet.meshx2.reliability

object RuntimeGovernanceChecklist {

    fun items(context: android.content.Context): List<GovernanceChecklistItem> {
        val signals = ReliabilitySignalCollector.collect(context)
        val readiness = ReliabilityReadinessGate.status(signals)
        val confidence = ReliabilitySnapshotAggregator.confidence()
        val pressure = ReliabilityPressureAggregator.snapshot(signals)
        val stress = RuntimeStressDiagnostics.highestSeverity(signals)
        return listOf(
            GovernanceChecklistItem(
                label = "readiness verified",
                status = when (readiness) {
                    ReliabilityReadinessStatus.READY -> GovernanceChecklistStatus.ACCEPTABLE
                    ReliabilityReadinessStatus.GUARDED -> GovernanceChecklistStatus.WARNING
                    ReliabilityReadinessStatus.BLOCKED -> GovernanceChecklistStatus.BLOCKED
                    ReliabilityReadinessStatus.UNKNOWN -> GovernanceChecklistStatus.PENDING
                },
                detail = readiness.name.lowercase()
            ),
            GovernanceChecklistItem(
                label = "telemetry confidence acceptable",
                status = when (confidence) {
                    ReliabilityObservationConfidence.HIGH -> GovernanceChecklistStatus.ACCEPTABLE
                    ReliabilityObservationConfidence.MEDIUM -> GovernanceChecklistStatus.INFORMATIONAL
                    ReliabilityObservationConfidence.LOW -> GovernanceChecklistStatus.WARNING
                    ReliabilityObservationConfidence.UNKNOWN -> GovernanceChecklistStatus.PENDING
                },
                detail = confidence.name.lowercase()
            ),
            GovernanceChecklistItem(
                label = "pressure acceptable",
                status = when (pressure.category) {
                    ReliabilityPressureScore.NOMINAL -> GovernanceChecklistStatus.ACCEPTABLE
                    ReliabilityPressureScore.ELEVATED -> GovernanceChecklistStatus.INFORMATIONAL
                    ReliabilityPressureScore.STRESSED -> GovernanceChecklistStatus.WARNING
                    ReliabilityPressureScore.OVERLOADED,
                    ReliabilityPressureScore.CRITICAL -> GovernanceChecklistStatus.BLOCKED
                },
                detail = pressure.category.name.lowercase()
            ),
            GovernanceChecklistItem(
                label = "stress acceptable",
                status = when (stress) {
                    RuntimeStressSeverity.INFORMATIONAL,
                    RuntimeStressSeverity.LOW -> GovernanceChecklistStatus.ACCEPTABLE
                    RuntimeStressSeverity.MODERATE -> GovernanceChecklistStatus.INFORMATIONAL
                    RuntimeStressSeverity.HIGH -> GovernanceChecklistStatus.WARNING
                    RuntimeStressSeverity.CRITICAL -> GovernanceChecklistStatus.BLOCKED
                },
                detail = stress.name.lowercase()
            ),
            GovernanceChecklistItem(
                label = "blocker review complete",
                status = if (readiness == ReliabilityReadinessStatus.BLOCKED) {
                    GovernanceChecklistStatus.WARNING
                } else {
                    GovernanceChecklistStatus.ACCEPTABLE
                },
                detail = "passive review required when blockers exist"
            ),
            GovernanceChecklistItem(
                label = "escalation review complete",
                status = if (pressure.category == ReliabilityPressureScore.STRESSED ||
                    pressure.category == ReliabilityPressureScore.OVERLOADED ||
                    pressure.category == ReliabilityPressureScore.CRITICAL
                ) {
                    GovernanceChecklistStatus.WARNING
                } else {
                    GovernanceChecklistStatus.INFORMATIONAL
                },
                detail = "conceptual escalation only"
            ),
            GovernanceChecklistItem(
                label = "rollback path reviewed",
                status = GovernanceChecklistStatus.INFORMATIONAL,
                detail = "rollback governance remains documentation-only"
            )
        )
    }

    fun report(context: android.content.Context): String =
        buildString {
            appendLine("GOVERNANCE CHECKLIST")
            appendLine("======================")
            items(context).forEach {
                appendLine("${it.label}: ${it.status.name.lowercase()} (${it.detail})")
            }
        }.trimEnd()
}
