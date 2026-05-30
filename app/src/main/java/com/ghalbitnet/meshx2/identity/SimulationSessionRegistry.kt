package com.ghalbitnet.meshx2.identity

object SimulationSessionRegistry {

    private val sessions =
        mutableListOf<SimulationSessionInfo>()

    fun all(): List<SimulationSessionInfo> =
        sessions.toList()

    fun count(): Int =
        sessions.size

    fun report(): String =
        "SIMULATION SESSION REGISTRY | count=${count()} | state=empty-by-default"
}
