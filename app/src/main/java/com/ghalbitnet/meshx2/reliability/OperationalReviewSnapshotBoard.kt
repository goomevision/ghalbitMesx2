package com.ghalbitnet.meshx2.reliability

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

object OperationalReviewSnapshotBoard {
    private val isBuildingSnapshot = AtomicBoolean(false)

    @Volatile
    private var lastKnownGoodSnapshot =
        OperationalReviewSnapshot(
            status = OperationalReviewStatus.REVIEW_REQUIRED,
            primaryBlocker = "snapshot belum tersedia",
            highestRisk = "unknown operator/runtime risk",
            telemetryConfidence = ReliabilityObservationConfidence.UNKNOWN.name.lowercase(),
            nextRecommendedReviewAction = "refresh telemetry and review governance state",
            blockers = emptyList(),
            approvalDecision = OperationalApprovalDecision.REVIEW_REQUIRED,
            reviewSessionState = ReviewSessionState.GUARDED
        )

    fun snapshot(context: android.content.Context): OperationalReviewSnapshot {
        if (!isBuildingSnapshot.compareAndSet(false, true)) {
            Log.w("GHALBIT-RELIABILITY", "recursion blocked in OperationalReviewSnapshotBoard")
            return lastKnownGoodSnapshot
        }
        Log.d("GHALBIT-REVIEW-SNAPSHOT", "build started")
        try {
        val signals = ReliabilitySignalCollector.collect(context)
        val blockers = ReliabilityBlockerReview.blockers(context)
        val readiness = ReliabilityReadinessGate.status(signals)
        val confidence = ReliabilitySnapshotAggregator.confidence()
        val pressure = ReliabilityPressureAggregator.snapshot(signals)
        val stress = RuntimeStressDiagnostics.highestSeverity(signals)
        val riskReport = RuntimeOperatorRiskBoard.report(signals).lowercase()
        val highestRisk =
            when {
                riskReport.contains("critical") -> "critical activation/runtime risk"
                riskReport.contains("high") -> "high operator/runtime risk"
                riskReport.contains("moderate") -> "moderate operator/runtime risk"
                else -> "low operator/runtime risk"
            }
        val blocker =
            when {
                readiness == ReliabilityReadinessStatus.BLOCKED -> "readiness gate blocked"
                confidence == ReliabilityObservationConfidence.LOW ||
                    confidence == ReliabilityObservationConfidence.UNKNOWN ->
                    "telemetry confidence degraded"
                stress == RuntimeStressSeverity.CRITICAL -> "critical stress state detected"
                pressure.category == ReliabilityPressureScore.CRITICAL -> "runtime pressure critical"
                else -> "-"
            }
        val status =
            when {
                blocker != "-" -> OperationalReviewStatus.BLOCKED
                readiness == ReliabilityReadinessStatus.UNKNOWN -> OperationalReviewStatus.REVIEW_REQUIRED
                readiness == ReliabilityReadinessStatus.GUARDED -> OperationalReviewStatus.GUARDED
                else -> OperationalReviewStatus.STABLE
            }
        val nextAction =
            when (status) {
                OperationalReviewStatus.BLOCKED -> "review blockers and keep activation disabled"
                OperationalReviewStatus.REVIEW_REQUIRED -> "refresh telemetry and review governance state"
                OperationalReviewStatus.GUARDED -> "continue observation under guarded review"
                OperationalReviewStatus.STABLE -> "maintain passive monitoring and periodic review"
            }
        val approvalChain =
            OperationalApprovalChain.from(
                ApprovalChainSnapshot(
                    readiness = readiness,
                    confidence = confidence,
                    blockers = blockers
                )
            )
        val reviewSessionState =
            when (approvalChain.decision) {
                OperationalApprovalDecision.REJECTED -> ReviewSessionState.BLOCKED
                OperationalApprovalDecision.REVIEW_REQUIRED,
                OperationalApprovalDecision.CONDITIONALLY_APPROVED -> ReviewSessionState.GUARDED
                OperationalApprovalDecision.APPROVED_BUT_DISABLED -> ReviewSessionState.OPEN
            }
        val snapshot =
            OperationalReviewSnapshot(
            status = status,
            primaryBlocker = blocker,
            highestRisk = highestRisk,
            telemetryConfidence = confidence.name.lowercase(),
            nextRecommendedReviewAction = nextAction,
            blockers = blockers,
            approvalDecision = approvalChain.decision,
            reviewSessionState = reviewSessionState
        )
        lastKnownGoodSnapshot = snapshot
        Log.d("GHALBIT-REVIEW-SNAPSHOT", "build completed status=${snapshot.status.name.lowercase()}")
        return snapshot
        } finally {
            isBuildingSnapshot.set(false)
        }
    }

    fun report(context: android.content.Context): String {
        val snapshot = snapshot(context)
        val governanceSummary = GovernanceSummaryBoard.summary(context, snapshot)
        return buildString {
            appendLine("OPERATIONAL REVIEW SNAPSHOT")
            appendLine("===========================")
            appendLine("Overall status: ${snapshot.status.name.lowercase()}")
            appendLine("Governance summary: ${governanceSummary.state.name.lowercase()}")
            appendLine("Primary blocker: ${snapshot.primaryBlocker}")
            appendLine("Highest risk: ${snapshot.highestRisk}")
            appendLine("Telemetry confidence: ${snapshot.telemetryConfidence}")
            appendLine("Approval decision: ${snapshot.approvalDecision.name.lowercase()}")
            appendLine("Review session state: ${snapshot.reviewSessionState.name.lowercase()}")
            appendLine(
                "Structured blockers: ${
                    if (snapshot.blockers.isEmpty()) "-" else snapshot.blockers.joinToString { it.label }
                }"
            )
            appendLine("Next recommended review action: ${snapshot.nextRecommendedReviewAction}")
        }.trimEnd()
    }
}
