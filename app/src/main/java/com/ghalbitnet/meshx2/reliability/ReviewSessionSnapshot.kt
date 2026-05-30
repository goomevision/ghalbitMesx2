package com.ghalbitnet.meshx2.reliability

data class ReviewSessionSnapshot(
    val contextLabel: String,
    val governanceState: String,
    val blockers: String,
    val approvalStatus: String,
    val rollbackReadiness: String,
    val activationEligibility: String,
    val state: ReviewSessionState
)
