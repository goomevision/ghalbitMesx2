package com.ghalbitnet.meshx2.simulation

enum class SimulationCategory {
    SIM_PASS,
    SIM_PARTIAL,
    SIM_FAIL,
    NEEDS_REAL_DEVICE_TEST
}

data class SimulationReport(
    val scenario: String,
    val category: SimulationCategory,
    val notes: List<String> = emptyList()
)

