package com.ghalbitnet.meshx2.reliability

object ReliabilityEscalationPolicy {

    fun level(signals: List<ReliabilitySignalSnapshot>): ReliabilityEscalationLevel {
        val readiness = ReliabilityReadinessGate.status(signals)
        val pressure = ReliabilityPressureAggregator.snapshot(signals)
        return when {
            readiness == ReliabilityReadinessStatus.BLOCKED -> ReliabilityEscalationLevel.ACTIVATION_REFUSED
            pressure.category == ReliabilityPressureScore.CRITICAL ||
                pressure.category == ReliabilityPressureScore.OVERLOADED -> ReliabilityEscalationLevel.SAFE_MODE_RECOMMENDED
            pressure.category == ReliabilityPressureScore.STRESSED -> ReliabilityEscalationLevel.HIGH
            pressure.category == ReliabilityPressureScore.ELEVATED -> ReliabilityEscalationLevel.ELEVATED
            else -> ReliabilityEscalationLevel.LOW
        }
    }

    fun reasons(signals: List<ReliabilitySignalSnapshot>): List<ReliabilityEscalationReason> {
        val pressure = ReliabilityPressureAggregator.snapshot(signals)
        val confidence = ReliabilitySnapshotAggregator.confidence()
        val reasons = mutableListOf<ReliabilityEscalationReason>()
        reasons += when (pressure.category) {
            ReliabilityPressureScore.NOMINAL -> ReliabilityEscalationReason.LOW_PRESSURE
            ReliabilityPressureScore.ELEVATED -> ReliabilityEscalationReason.ELEVATED_PRESSURE
            ReliabilityPressureScore.STRESSED,
            ReliabilityPressureScore.OVERLOADED,
            ReliabilityPressureScore.CRITICAL -> ReliabilityEscalationReason.HIGH_CONGESTION
        }
        if (pressure.relayPressureScore >= 40) {
            reasons += ReliabilityEscalationReason.CUSTODY_OVERLOAD_RISK
        }
        if (pressure.retryPressureScore >= 40) {
            reasons += ReliabilityEscalationReason.RETRY_SATURATION_RISK
        }
        if (ReliabilitySnapshotAggregator.snapshots().any {
                ReliabilitySnapshotAggregator.freshnessState(it) != SnapshotFreshnessState.FRESH
            }
        ) {
            reasons += ReliabilityEscalationReason.STALE_TELEMETRY_RISK
        }
        if (confidence == ReliabilityObservationConfidence.LOW || confidence == ReliabilityObservationConfidence.UNKNOWN) {
            reasons += ReliabilityEscalationReason.DEGRADED_OBSERVATION_CONFIDENCE
        }
        return reasons.distinct()
    }

    fun report(signals: List<ReliabilitySignalSnapshot>): String =
        buildString {
            appendLine("RELIABILITY ESCALATION")
            appendLine("======================")
            appendLine("Level: ${level(signals).name.lowercase()}")
            appendLine("Reasons: ${reasons(signals).joinToString { it.name.lowercase() }}")
            appendLine("Actions: documentation only")
        }.trimEnd()
}
