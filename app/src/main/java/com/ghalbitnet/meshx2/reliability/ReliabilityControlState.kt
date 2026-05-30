package com.ghalbitnet.meshx2.reliability

enum class ReliabilityControlState {
    DISABLED,
    OBSERVATIONAL_ONLY,
    GUARDED,
    BLOCKED,
    REVIEW_REQUIRED,
    ACTIVATION_READY
}
