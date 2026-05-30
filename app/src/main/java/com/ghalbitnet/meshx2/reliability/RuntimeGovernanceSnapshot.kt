package com.ghalbitnet.meshx2.reliability

data class RuntimeGovernanceSnapshot(
    val healthLevel: GovernanceHealthLevel,
    val observationState: GovernanceObservationState,
    val activationEligible: Boolean,
    val freshnessState: SnapshotFreshnessState,
    val stressSeverity: RuntimeStressSeverity
)
