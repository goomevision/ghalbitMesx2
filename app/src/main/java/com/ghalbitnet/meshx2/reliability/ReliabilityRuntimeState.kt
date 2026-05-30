package com.ghalbitnet.meshx2.reliability

enum class ReliabilityRuntimeState {
    QUEUE_STATE,
    RETRY_STATE,
    DELAYED_SYNC_STATE,
    RELAY_CUSTODY_STATE,
    RUNTIME_STRESS_STATE,
    READINESS_STATE
}
