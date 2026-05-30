package com.ghalbitnet.meshx2.reliability

enum class ReliabilityControlDecision {
    KEEP_OBSERVATIONAL,
    REQUIRE_REVIEW,
    BLOCK_ACTIVATION,
    READY_BUT_DISABLED
}
