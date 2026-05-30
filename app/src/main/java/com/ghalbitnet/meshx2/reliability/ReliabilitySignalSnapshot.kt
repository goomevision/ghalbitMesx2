package com.ghalbitnet.meshx2.reliability

data class ReliabilitySignalSnapshot(
    val type: ReliabilitySignalType,
    val value: String,
    val label: String,
    val capturedAt: Long = System.currentTimeMillis()
)
