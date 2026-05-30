package com.ghalbitnet.meshx2.identity

data class SimulationRuntimeState(
    val enabled: Boolean = false,
    val status: String = "disabled",
    val note: String = "Dormant only; no active simulation yet."
)
