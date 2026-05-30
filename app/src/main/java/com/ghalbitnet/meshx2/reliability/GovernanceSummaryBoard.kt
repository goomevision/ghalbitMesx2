package com.ghalbitnet.meshx2.reliability

import android.util.Log

object GovernanceSummaryBoard {
    @Volatile
    private var lastKnownGoodSummary =
        GovernanceSummary(
            state = GovernanceSummaryState.UNKNOWN,
            operationalHealth = "unknown",
            readiness = "unknown",
            governanceConfidence = "unknown",
            escalationState = "unknown",
            activationState = "review_required",
            blockerSeverity = "none",
            rollbackReadiness = "unknown"
        )

    fun summary(
        context: android.content.Context,
        reviewSnapshot: OperationalReviewSnapshot
    ): GovernanceSummary {
        val signals = ReliabilitySignalCollector.collect(context)
        val readiness = ReliabilityReadinessGate.status(signals).name.lowercase()
        val confidence = ReliabilitySnapshotAggregator.confidence().name.lowercase()
        val pressure = ReliabilityPressureAggregator.snapshot(signals).category.name.lowercase()
        val blockers = reviewSnapshot.blockers
        val approval = reviewSnapshot.approvalDecision.name.lowercase()
        val rollback = RollbackGovernanceReview.from(reviewSnapshot).readinessLevel.name.lowercase()
        val state =
            when {
                blockers.any { it.severity == ReliabilityBlockerSeverity.CRITICAL } -> GovernanceSummaryState.BLOCKED
                blockers.any { it.severity == ReliabilityBlockerSeverity.HIGH } -> GovernanceSummaryState.DEGRADED
                readiness == "guarded" -> GovernanceSummaryState.GUARDED
                readiness == "ready" -> GovernanceSummaryState.HEALTHY
                else -> GovernanceSummaryState.UNKNOWN
            }
        val summary =
            GovernanceSummary(
            state = state,
            operationalHealth = pressure,
            readiness = readiness,
            governanceConfidence = confidence,
            escalationState = ReliabilityEscalationPolicy.report(signals).lineSequence().drop(2).firstOrNull()?.substringAfter(": ") ?: "unknown",
            activationState = approval,
            blockerSeverity = blockers.maxByOrNull { it.severity.ordinal }?.severity?.name?.lowercase() ?: "none",
            rollbackReadiness = rollback
        )
        lastKnownGoodSummary = summary
        Log.d("GHALBIT-GOVERNANCE", "summary generated state=${summary.state.name.lowercase()}")
        return summary
    }

    fun lastKnownGood(): GovernanceSummary {
        return lastKnownGoodSummary
    }

    fun report(context: android.content.Context): String {
        val summary = summary(context, OperationalReviewSnapshotBoard.snapshot(context))
        return buildString {
            appendLine("GOVERNANCE SUMMARY")
            appendLine("======================")
            appendLine("State: ${summary.state.name.lowercase()}")
            appendLine("Operational health: ${summary.operationalHealth}")
            appendLine("Readiness: ${summary.readiness}")
            appendLine("Governance confidence: ${summary.governanceConfidence}")
            appendLine("Escalation state: ${summary.escalationState}")
            appendLine("Activation state: ${summary.activationState}")
            appendLine("Blocker severity: ${summary.blockerSeverity}")
            appendLine("Rollback readiness: ${summary.rollbackReadiness}")
        }.trimEnd()
    }
}
