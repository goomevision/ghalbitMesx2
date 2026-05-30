package com.ghalbitnet.meshx2.reliability

data class ReliabilityRuntimeSnapshot(
    val state: ReliabilityRuntimeState,
    val capturedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = capturedAt + 15_000L,
    val confidenceLabel: String = "observational"
)
