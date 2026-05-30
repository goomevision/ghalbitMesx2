package com.ghalbitnet.meshx2.reliability

data class OperationalApprovalChain(
    val decision: OperationalApprovalDecision,
    val stages: List<OperationalApprovalStage>,
    val note: String
) {
    companion object {
        fun from(snapshot: ApprovalChainSnapshot): OperationalApprovalChain {
            val readiness = snapshot.readiness
            val confidence = snapshot.confidence
            val blockers = snapshot.blockers
            val decision =
                when {
                    blockers.any { it.severity == ReliabilityBlockerSeverity.CRITICAL } ->
                        OperationalApprovalDecision.REJECTED
                    readiness == ReliabilityReadinessStatus.BLOCKED ->
                        OperationalApprovalDecision.REJECTED
                    readiness == ReliabilityReadinessStatus.UNKNOWN ||
                        confidence == ReliabilityObservationConfidence.LOW ||
                        confidence == ReliabilityObservationConfidence.UNKNOWN ->
                        OperationalApprovalDecision.REVIEW_REQUIRED
                    readiness == ReliabilityReadinessStatus.GUARDED ->
                        OperationalApprovalDecision.CONDITIONALLY_APPROVED
                    else ->
                        OperationalApprovalDecision.APPROVED_BUT_DISABLED
                }
            val stages =
                OperationalApprovalStage.entries.filter {
                    when (decision) {
                        OperationalApprovalDecision.REJECTED -> it.ordinal <= OperationalApprovalStage.BLOCKER_REVIEW_APPROVED.ordinal
                        OperationalApprovalDecision.REVIEW_REQUIRED -> it.ordinal <= OperationalApprovalStage.GOVERNANCE_APPROVED.ordinal
                        OperationalApprovalDecision.CONDITIONALLY_APPROVED,
                        OperationalApprovalDecision.APPROVED_BUT_DISABLED -> true
                    }
                }
            val note =
                when (decision) {
                    OperationalApprovalDecision.REJECTED -> "approval chain halted on passive blockers"
                    OperationalApprovalDecision.REVIEW_REQUIRED -> "approval chain requires additional passive review"
                    OperationalApprovalDecision.CONDITIONALLY_APPROVED -> "approval is guarded and runtime stays disabled"
                    OperationalApprovalDecision.APPROVED_BUT_DISABLED -> "approval is informational only while runtime remains disabled"
                }
            android.util.Log.d("GHALBIT-RELIABILITY", "approval chain built decision=${decision.name.lowercase()}")
            return OperationalApprovalChain(decision, stages, note)
        }

        fun from(context: android.content.Context): OperationalApprovalChain {
            val signals = ReliabilitySignalCollector.collect(context)
            return from(
                ApprovalChainSnapshot(
                    readiness = ReliabilityReadinessGate.status(signals),
                    confidence = ReliabilitySnapshotAggregator.confidence(),
                    blockers = ReliabilityBlockerReview.blockers(context)
                )
            )
        }
    }
}
