package com.ghalbitnet.meshx2.reliability

object UnifiedOperatorReviewBoard {

    fun severity(context: android.content.Context): UnifiedReviewSeverity {
        val snapshot = OperationalReviewSnapshotBoard.snapshot(context)
        return when (snapshot.status) {
            OperationalReviewStatus.STABLE -> UnifiedReviewSeverity.INFORMATIONAL
            OperationalReviewStatus.GUARDED -> UnifiedReviewSeverity.GUARDED
            OperationalReviewStatus.REVIEW_REQUIRED -> UnifiedReviewSeverity.HIGH
            OperationalReviewStatus.BLOCKED -> UnifiedReviewSeverity.CRITICAL
        }
    }

    fun report(context: android.content.Context): String {
        val signals = ReliabilitySignalCollector.collect(context)
        val reviewSnapshot = OperationalReviewSnapshotBoard.snapshot(context)
        val governanceSummary = GovernanceSummaryBoard.summary(context, reviewSnapshot)
        val readiness = ReliabilityReadinessGate.status(signals)
        val pressure = ReliabilityPressureAggregator.snapshot(signals)
        val stress = RuntimeStressDiagnostics.highestSeverity(signals)
        val confidence = ReliabilitySnapshotAggregator.confidence()
        val blockers = ReliabilityBlockerReview.blockers(context)
        val approval = OperationalApprovalChain.from(
            ApprovalChainSnapshot(
                readiness = readiness,
                confidence = confidence,
                blockers = blockers
            )
        )
        val rollback = RollbackGovernanceReview.from(reviewSnapshot)
        val escalation = EscalationVisibilityBoard.entry(context)
        val session = ReviewSessionSummary.snapshot(context)
        val governance = RuntimeGovernanceSnapshot(
            healthLevel = when (readiness) {
                ReliabilityReadinessStatus.READY -> GovernanceHealthLevel.HEALTHY
                ReliabilityReadinessStatus.GUARDED -> GovernanceHealthLevel.GUARDED
                ReliabilityReadinessStatus.BLOCKED -> GovernanceHealthLevel.BLOCKED
                ReliabilityReadinessStatus.UNKNOWN -> GovernanceHealthLevel.RISKY
            },
            observationState = when (confidence) {
                ReliabilityObservationConfidence.HIGH -> GovernanceObservationState.OBSERVATIONAL
                ReliabilityObservationConfidence.MEDIUM -> GovernanceObservationState.MIXED
                ReliabilityObservationConfidence.LOW -> GovernanceObservationState.PLACEHOLDER_HEAVY
                ReliabilityObservationConfidence.UNKNOWN -> GovernanceObservationState.DERIVED
            },
            activationEligible = false,
            freshnessState = ReliabilitySnapshotAggregator.snapshots()
                .map { ReliabilitySnapshotAggregator.freshnessState(it) }
                .maxByOrNull { it.ordinal } ?: SnapshotFreshnessState.FRESH,
            stressSeverity = stress
        )
        return buildString {
            appendLine("UNIFIED OPERATOR REVIEW")
            appendLine("=======================")
            appendLine("Board severity: ${severity(context).name.lowercase()}")
            appendLine("Governance summary: ${governanceSummary.state.name.lowercase()}")
            appendLine("Readiness state: ${readiness.name.lowercase()}")
            appendLine("Governance state: ${governance.healthLevel.name.lowercase()}")
            appendLine("Telemetry confidence: ${confidence.name.lowercase()}")
            appendLine("Runtime pressure: ${pressure.category.name.lowercase()} (${pressure.overallRuntimeHealthScore})")
            appendLine("Stress state: ${stress.name.lowercase()}")
            appendLine("Blockers: ${if (blockers.isEmpty()) "-" else blockers.joinToString { it.label }}")
            appendLine("Approval chain: ${approval.decision.name.lowercase()}")
            appendLine("Rollback readiness: ${rollback.readinessLevel.name.lowercase()}")
            appendLine("Escalation visibility: ${escalation.state.name.lowercase()} (${escalation.reason})")
            appendLine("Review session: ${session.state.name.lowercase()} (${session.approvalStatus})")
            appendLine(
                "Sections: ${
                    UnifiedReviewSection.entries.joinToString { it.name.lowercase() }
                }"
            )
        }.trimEnd()
    }
}
