package com.ghalbitnet.meshx2.call

import android.util.Log
import java.util.TreeMap

class AdaptiveJitterBuffer(
    private var targetBufferMs: Long = 80L,
    private val maxGapMs: Long = 250L
) {
    private val packets = TreeMap<Int, VoicePacket>()
    private var expectedSequence = 0

    fun updateTarget(bufferMs: Long) {
        targetBufferMs = bufferMs.coerceIn(60L, 180L)
        Log.d("GHALBIT-AUDIO-JITTER", "bufferMs=$targetBufferMs")
    }

    fun offer(packet: VoicePacket) {
        packets.putIfAbsent(packet.sequence, packet)
    }

    fun drainReady(now: Long = System.currentTimeMillis()): List<VoicePacket> {
        if (packets.isEmpty()) return emptyList()
        if (expectedSequence == 0) expectedSequence = packets.firstKey()
        val ready = mutableListOf<VoicePacket>()
        while (true) {
            val next = packets[expectedSequence]
            if (next != null) {
                ready += next
                packets.remove(expectedSequence)
                expectedSequence++
                continue
            }
            val earliest = packets.firstEntry()?.value ?: break
            if (now - earliest.timestamp >= maxGapMs) {
                Log.d("GHALBIT-VOICE-JITTER", "late dropped")
                Log.d("GHALBIT-VOICE-JITTER", "conceal frame")
                expectedSequence++
                continue
            }
            break
        }
        return ready
    }
}
