package com.ghalbitnet.meshx2.identity

data class SimulationEvent(
    val type: SimulationEventType,
    val label: String,
    val detail: String,
    val timestamp: Long = System.currentTimeMillis()
)
