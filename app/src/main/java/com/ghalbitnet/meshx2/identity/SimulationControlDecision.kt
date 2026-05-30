package com.ghalbitnet.meshx2.identity

enum class SimulationControlDecision {
    ALLOW_DIAGNOSTICS_ONLY,
    BLOCK_ACTIVATION,
    REQUIRE_REVIEW,
    REQUIRE_ROLLBACK_READINESS,
    REQUIRE_FRESH_DIAGNOSTICS
}
