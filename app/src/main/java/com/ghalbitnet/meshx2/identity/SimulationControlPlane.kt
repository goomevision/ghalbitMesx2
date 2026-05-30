package com.ghalbitnet.meshx2.identity

data class SimulationControlPlane(
    val name: String = "DormantSimulationControlPlane",
    val active: Boolean = false,
    val note: String = "Passive only; no simulation activation or runtime execution."
)
