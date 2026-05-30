package com.ghalbitnet.meshx2.identity

data class SimulationControlRequest(
    val operatorLabel: String,
    val scope: SimulationScope,
    val requestedAt: Long = System.currentTimeMillis()
)
