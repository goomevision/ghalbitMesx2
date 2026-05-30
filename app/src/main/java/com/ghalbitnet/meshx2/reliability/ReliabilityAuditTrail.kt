package com.ghalbitnet.meshx2.reliability

object ReliabilityAuditTrail {

    private fun events(signals: List<ReliabilitySignalSnapshot>): List<ReliabilityAuditEvent> {
        val pressure = ReliabilityPressureAggregator.snapshot(signals)
        val stress = RuntimeStressDiagnostics.highestSeverity(signals)
        val confidence = ReliabilitySnapshotAggregator.confidence()
        val readiness = ReliabilityReadinessGate.status(signals)
        val list = mutableListOf<ReliabilityAuditEvent>()

        if (readiness == ReliabilityReadinessStatus.GUARDED || readiness == ReliabilityReadinessStatus.BLOCKED) {
            list += ReliabilityAuditEvent(
                reason = ReliabilityAuditReason.READINESS_DOWNGRADE,
                detail = "Readiness=${readiness.name.lowercase()}"
            )
        }
        if (pressure.category == ReliabilityPressureScore.STRESSED ||
            pressure.category == ReliabilityPressureScore.OVERLOADED ||
            pressure.category == ReliabilityPressureScore.CRITICAL
        ) {
            list += ReliabilityAuditEvent(
                reason = ReliabilityAuditReason.PRESSURE_ESCALATION,
                detail = "Pressure=${pressure.category.name.lowercase()} score=${pressure.overallRuntimeHealthScore}"
            )
        }
        if (stress == RuntimeStressSeverity.HIGH || stress == RuntimeStressSeverity.CRITICAL) {
            list += ReliabilityAuditEvent(
                reason = ReliabilityAuditReason.STRESS_ESCALATION,
                detail = "Stress=${stress.name.lowercase()}"
            )
        }
        if (readiness == ReliabilityReadinessStatus.BLOCKED) {
            list += ReliabilityAuditEvent(
                reason = ReliabilityAuditReason.ACTIVATION_REFUSAL,
                detail = "Activation remains blocked by readiness gate"
            )
        }
        if (confidence == ReliabilityObservationConfidence.LOW || confidence == ReliabilityObservationConfidence.UNKNOWN) {
            list += ReliabilityAuditEvent(
                reason = ReliabilityAuditReason.CONFIDENCE_DEGRADATION,
                detail = "Observation confidence=${confidence.name.lowercase()}"
            )
        }
        if (ReliabilitySnapshotAggregator.snapshots().any {
                ReliabilitySnapshotAggregator.freshnessState(it) == SnapshotFreshnessState.STALE ||
                    ReliabilitySnapshotAggregator.freshnessState(it) == SnapshotFreshnessState.EXPIRED
            }
        ) {
            list += ReliabilityAuditEvent(
                reason = ReliabilityAuditReason.STALE_TELEMETRY,
                detail = "One or more reliability snapshots are stale"
            )
        }
        if (readiness == ReliabilityReadinessStatus.GUARDED) {
            list += ReliabilityAuditEvent(
                reason = ReliabilityAuditReason.SAFE_MODE_RECOMMENDATION,
                detail = "Guarded state suggests conservative operator posture"
            )
        }
        return list
    }

    fun report(signals: List<ReliabilitySignalSnapshot>): String =
        buildString {
            appendLine("RELIABILITY AUDIT")
            appendLine("======================")
            val current = events(signals)
            if (current.isEmpty()) {
                appendLine("No passive audit events")
            } else {
                current.forEach {
                    appendLine("${it.reason.name.lowercase()}: ${it.detail}")
                }
            }
        }.trimEnd()
}
