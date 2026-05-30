package com.ghalbitnet.meshx2.call

data class VoiceChunk(
    val chunkId: String,
    val callId: String,
    val senderGlobalId: String,
    val sequenceNumber: Int,
    val capturedAt: Long,
    val durationMs: Int,
    val codec: String,
    val compressedBytes: ByteArray,
    val checksum: String,
    val isLastInBurst: Boolean
)
