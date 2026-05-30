package com.ghalbitnet.meshx2.reliability

enum class ReliabilityEscalationReason {
    LOW_PRESSURE,
    ELEVATED_PRESSURE,
    HIGH_CONGESTION,
    STALE_TELEMETRY_RISK,
    CUSTODY_OVERLOAD_RISK,
    RETRY_SATURATION_RISK,
    DEGRADED_OBSERVATION_CONFIDENCE
}
