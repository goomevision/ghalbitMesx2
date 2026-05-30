package com.ghalbitnet.meshx2.reliability

data class OperationalReviewSnapshot(
    val status: OperationalReviewStatus,
    val primaryBlocker: String,
    val highestRisk: String,
    val telemetryConfidence: String,
    val nextRecommendedReviewAction: String,
    val blockers: List<ReliabilityBlocker>,
    val approvalDecision: OperationalApprovalDecision,
    val reviewSessionState: ReviewSessionState
)
