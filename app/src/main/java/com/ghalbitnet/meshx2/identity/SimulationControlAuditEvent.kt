package com.ghalbitnet.meshx2.identity

data class SimulationControlAuditEvent(
    val eventId: String,
    val timestamp: Long,
    val operatorLabel: String,
    val scope: SimulationScope,
    val decision: String,
    val reason: String,
    val safetyScore: Int,
    val rollbackReadiness: String
)
