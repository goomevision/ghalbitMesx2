package com.ghalbitnet.meshx2.identity

data class SimulationSession(
    val sessionId: String,
    val scope: SimulationScope,
    val createdAt: Long,
    val state: String,
    val rollbackReady: Boolean
)
