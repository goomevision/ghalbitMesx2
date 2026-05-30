package com.ghalbitnet.meshx2.reliability

data class RuntimeQueueMetrics(
    val queueDepth: Int = 0,
    val averageAgeMillis: Long = 0L,
    val retryPressure: Int = 0,
    val expiredCount: Int = 0,
    val stalledCount: Int = 0,
    val relayBacklog: Int = 0,
    val lowBandwidthDeferCount: Int = 0
)
