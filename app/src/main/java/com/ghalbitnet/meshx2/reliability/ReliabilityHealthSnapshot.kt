package com.ghalbitnet.meshx2.reliability

data class ReliabilityHealthSnapshot(
    val queuePressureScore: Int,
    val retryPressureScore: Int,
    val relayPressureScore: Int,
    val syncPressureScore: Int,
    val overallRuntimeHealthScore: Int,
    val category: ReliabilityPressureScore
)
