package com.ghalbitnet.meshx2.call

import android.util.Log
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

class VoiceCapacitorBuffer(
    private val callId: String,
    private val senderGlobalId: String
) {
    private val sequence = AtomicInteger(0)
    private val pending = ArrayDeque<VoiceChunk>()

    fun captureChunk(bytes: ByteArray, durationMs: Int, codec: String, isLastInBurst: Boolean): VoiceChunk {
        val seq = sequence.incrementAndGet()
        val chunk =
            VoiceChunk(
                chunkId = "$callId-$seq",
                callId = callId,
                senderGlobalId = senderGlobalId,
                sequenceNumber = seq,
                capturedAt = System.currentTimeMillis(),
                durationMs = durationMs,
                codec = codec,
                compressedBytes = bytes,
                checksum = checksum(bytes),
                isLastInBurst = isLastInBurst
            )
        pending += chunk
        Log.d("GHALBIT-VOICE-CAPACITOR", "capture chunk seq=$seq")
        return chunk
    }

    fun drainBurst(maxChunks: Int = 6): List<VoiceChunk> {
        val burst = mutableListOf<VoiceChunk>()
        while (pending.isNotEmpty() && burst.size < maxChunks) {
            val next = pending.removeFirst()
            burst += next
            if (next.isLastInBurst) break
        }
        if (burst.isNotEmpty()) {
            Log.d("GHALBIT-VOICE-CAPACITOR", "burst ready chunks=${burst.size}")
        }
        return burst
    }

    fun queueSize(): Int = pending.size

    private fun checksum(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
