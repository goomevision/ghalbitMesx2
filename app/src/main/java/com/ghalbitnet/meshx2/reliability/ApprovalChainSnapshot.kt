package com.ghalbitnet.meshx2.reliability

data class ApprovalChainSnapshot(
    val readiness: ReliabilityReadinessStatus,
    val confidence: ReliabilityObservationConfidence,
    val blockers: List<ReliabilityBlocker>
)

