package com.ghalbitnet.meshx2.reliability

data class GovernanceSummary(
    val state: GovernanceSummaryState,
    val operationalHealth: String,
    val readiness: String,
    val governanceConfidence: String,
    val escalationState: String,
    val activationState: String,
    val blockerSeverity: String,
    val rollbackReadiness: String
)
