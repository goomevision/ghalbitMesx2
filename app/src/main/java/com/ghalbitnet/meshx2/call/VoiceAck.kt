package com.ghalbitnet.meshx2.call

data class VoiceAck(
    val sessionId: String,
    val lastReceivedSequence: Int,
    val missingSequences: List<Int> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
