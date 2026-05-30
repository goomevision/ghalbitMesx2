package com.ghalbitnet.meshx2.reliability

data class AdaptiveReliabilityPolicy(
    val state: ReliabilityPolicyState = ReliabilityPolicyState.DORMANT,
    val reasons: List<ReliabilityAdaptationReason> = emptyList(),
    val note: String = "Passive design only"
)
