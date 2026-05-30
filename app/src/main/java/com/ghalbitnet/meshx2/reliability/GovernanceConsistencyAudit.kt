package com.ghalbitnet.meshx2.reliability

object GovernanceConsistencyAudit {

    fun issues(context: android.content.Context): List<GovernanceConsistencyIssue> {
        val signals = ReliabilitySignalCollector.collect(context)
        val reviewSnapshot = OperationalReviewSnapshotBoard.snapshot(context)
        val readiness = ReliabilityReadinessGate.status(signals)
        val risk = RuntimeOperatorRiskBoard.report(signals).lowercase()
        val approval = OperationalApprovalChain.from(context)
        val rollback = RollbackGovernanceReview.from(reviewSnapshot)
        val blockers = ReliabilityBlockerReview.blockers(context)
        val escalation = EscalationVisibilityBoard.entry(context)
        val confidence = ReliabilitySnapshotAggregator.confidence()
        val unified = UnifiedOperatorReviewBoard.severity(context)
        val session = ReviewSessionSummary.snapshot(context)
        val issues = mutableListOf<GovernanceConsistencyIssue>()

        if (readiness == ReliabilityReadinessStatus.READY &&
            approval.decision == OperationalApprovalDecision.REJECTED
        ) {
            issues += GovernanceConsistencyIssue(
                "approval mismatch",
                "readiness is ready while approval chain is rejected"
            )
        }
        if (escalation.state == EscalationVisibilityState.CRITICAL &&
            unified != UnifiedReviewSeverity.CRITICAL
        ) {
            issues += GovernanceConsistencyIssue(
                "inconsistent escalation states",
                "critical escalation is not reflected in unified severity"
            )
        }
        if ((confidence == ReliabilityObservationConfidence.LOW ||
                confidence == ReliabilityObservationConfidence.UNKNOWN) &&
            !risk.contains("stale telemetry")
        ) {
            issues += GovernanceConsistencyIssue(
                "stale governance summaries",
                "telemetry confidence is degraded but risk board does not highlight stale telemetry"
            )
        }
        if (blockers.isEmpty() && session.blockers != "-") {
            issues += GovernanceConsistencyIssue(
                "blocker mismatch",
                "review session still reports blockers while blocker review is empty"
            )
        }
        if (rollback.state == RollbackGovernanceState.REVIEW_PENDING &&
            approval.decision == OperationalApprovalDecision.APPROVED_BUT_DISABLED
        ) {
            issues += GovernanceConsistencyIssue(
                "approval mismatch",
                "approval is higher than rollback review state suggests"
            )
        }
        if (session.state == ReviewSessionState.GUARDED &&
            readiness == ReliabilityReadinessStatus.BLOCKED
        ) {
            issues += GovernanceConsistencyIssue(
                "stale review snapshots",
                "review session remained guarded while readiness is blocked"
            )
        }
        return issues
    }

    fun report(context: android.content.Context): String =
        buildString {
            appendLine("GOVERNANCE CONSISTENCY AUDIT")
            appendLine("============================")
            val issues = issues(context)
            if (issues.isEmpty()) {
                appendLine("No passive consistency issues detected")
            } else {
                issues.forEach { appendLine("${it.label}: ${it.detail}") }
            }
        }.trimEnd()
}
