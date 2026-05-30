package com.ghalbitnet.meshx2.identity

object SimulationRuntimeShell {

    // Dormant shell only. This object must not start or execute any simulation.
    // No ownership changes, no routing changes, and no active runtime behavior.
    private val state =
        SimulationRuntimeState()

    fun currentState(): SimulationRuntimeState =
        state

    fun report(): String =
        "SIMULATION RUNTIME SHELL | enabled=${state.enabled} | status=${state.status} | note=${state.note}"
}
