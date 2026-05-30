package com.ghalbitnet.meshx2.reliability

enum class RetryPolicy {
    CONSERVATIVE,
    ADAPTIVE,
    EMERGENCY_PRIORITY,
    BATTERY_AWARE,
    LOW_BANDWIDTH_AWARE
}
