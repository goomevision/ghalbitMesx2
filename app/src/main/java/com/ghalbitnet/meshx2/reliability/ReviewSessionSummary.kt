package com.ghalbitnet.meshx2.reliability

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

object ReviewSessionSummary {
    private val isBuildingSnapshot = AtomicBoolean(false)

    @Volatile
    private var lastKnownGoodSnapshot =
        ReviewSessionSnapshot(
            contextLabel = "passive reliability governance review",
            governanceState = "unknown",
            blockers = "-",
            approvalStatus = OperationalApprovalDecision.REVIEW_REQUIRED.name.lowercase(),
            rollbackReadiness = "unknown",
            activationEligibility = "unknown",
            state = ReviewSessionState.GUARDED
        )

    fun snapshot(context: android.content.Context): ReviewSessionSnapshot {
        if (!isBuildingSnapshot.compareAndSet(false, true)) {
            Log.w("GHALBIT-RELIABILITY", "recursion blocked in ReviewSessionSummary")
            return lastKnownGoodSnapshot
        }
        Log.d("GHALBIT-REVIEW-SNAPSHOT", "review session build started")
        try {
        val reviewSnapshot = OperationalReviewSnapshotBoard.snapshot(context)
        val blockers = reviewSnapshot.blockers
        val approval = reviewSnapshot.approvalDecision
        val rollback = RollbackGovernanceReview.from(reviewSnapshot)
        val activation = ActivationEligibilityReviewBoard.report(context)
            .lineSequence()
            .firstOrNull { it.startsWith("State:") }
            ?.substringAfter(": ")
            ?.trim()
            ?: "unknown"
        val governanceState =
            when (reviewSnapshot.status) {
                OperationalReviewStatus.BLOCKED -> GovernanceSummaryState.BLOCKED
                OperationalReviewStatus.GUARDED -> GovernanceSummaryState.GUARDED
                OperationalReviewStatus.STABLE -> GovernanceSummaryState.HEALTHY
                OperationalReviewStatus.REVIEW_REQUIRED -> GovernanceSummaryState.UNKNOWN
            }
        val state =
            when (approval) {
                OperationalApprovalDecision.REJECTED -> ReviewSessionState.BLOCKED
                OperationalApprovalDecision.REVIEW_REQUIRED,
                OperationalApprovalDecision.CONDITIONALLY_APPROVED -> ReviewSessionState.GUARDED
                OperationalApprovalDecision.APPROVED_BUT_DISABLED -> ReviewSessionState.OPEN
            }
        val snapshot =
            ReviewSessionSnapshot(
            contextLabel = "passive reliability governance review",
            governanceState = governanceState.name.lowercase(),
            blockers = if (blockers.isEmpty()) "-" else blockers.joinToString { it.label },
            approvalStatus = approval.name.lowercase(),
            rollbackReadiness = rollback.readinessLevel.name.lowercase(),
            activationEligibility = activation.lowercase(),
            state = state
        )
        lastKnownGoodSnapshot = snapshot
        Log.d("GHALBIT-REVIEW-SNAPSHOT", "review session build completed state=${snapshot.state.name.lowercase()}")
        return snapshot
        } finally {
            isBuildingSnapshot.set(false)
        }
    }

    fun report(context: android.content.Context): String {
        val snapshot = snapshot(context)
        return buildString {
            appendLine("REVIEW SESSION SNAPSHOT")
            appendLine("=======================")
            appendLine("Review context: ${snapshot.contextLabel}")
            appendLine("Governance state: ${snapshot.governanceState}")
            appendLine("Blockers: ${snapshot.blockers}")
            appendLine("Approval status: ${snapshot.approvalStatus}")
            appendLine("Rollback readiness: ${snapshot.rollbackReadiness}")
            appendLine("Activation eligibility: ${snapshot.activationEligibility}")
            appendLine("Session state: ${snapshot.state.name.lowercase()}")
        }.trimEnd()
    }
}
