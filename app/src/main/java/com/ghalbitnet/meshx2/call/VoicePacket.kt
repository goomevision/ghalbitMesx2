package com.ghalbitnet.meshx2.call

enum class VoicePacketPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}

data class VoicePacket(
    val sessionId: String,
    val senderId: String,
    val sequence: Int,
    val timestamp: Long,
    val mode: AdaptiveVoiceMode,
    val payload: ByteArray,
    val priority: VoicePacketPriority = VoicePacketPriority.NORMAL,
    val checksum: Int = payload.contentHashCode()
)
