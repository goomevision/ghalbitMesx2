package com.ghalbitnet.meshx2.identity

data class SimulationSessionInfo(
    val sessionId: String,
    val createdAt: Long,
    val operatorLabel: String,
    val scope: SimulationScope,
    val state: SimulationSessionStatus,
    val diagnosticsReadiness: String
)
