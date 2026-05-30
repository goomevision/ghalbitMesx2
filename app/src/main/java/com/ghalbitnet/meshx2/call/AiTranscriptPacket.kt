package com.ghalbitnet.meshx2.call

data class AiTranscriptPacket(
    val sessionId: String,
    val senderId: String,
    val text: String,
    val priority: String,
    val emotionHint: String? = null,
    val timestamp: Long,
    val sourceMode: String,
    val language: String = "id-ID",
    val confidence: Float = 0f,
    val sequenceNumber: Int,
    val signature: String = "LOCAL_ONLY"
)
