package com.ghalbitnet.meshx2.call

import android.util.Log
import java.util.TreeMap

class AudioPacketJitterBuffer(
    private val frameBytes: Int,
    private val targetFrames: Int = 6
) {
    private val packets = TreeMap<Int, ByteArray>()
    private var nextSequence = 0
    private var started = false

    @Synchronized
    fun clear() {
        packets.clear()
        nextSequence = 0
        started = false
    }

    @Synchronized
    fun offer(sequenceNumber: Int, audioData: ByteArray) {
        packets[sequenceNumber] = audioData
        if (!started && packets.size >= targetFrames) {
            nextSequence = packets.firstKey()
            started = true
        }
        Log.d("GHALBIT-VOICE-PACKET", "received seq=$sequenceNumber")
        Log.d("GHALBIT-VOICE-JITTER", "buffer=${packets.size}")
    }

    @Synchronized
    fun pollFrame(): ByteArray {
        if (!started) {
            return ByteArray(frameBytes)
        }

        val expected = nextSequence++
        val frame = packets.remove(expected)
        if (frame != null) {
            return frame
        }

        Log.d("GHALBIT-VOICE-JITTER", "late dropped")
        Log.d("GHALBIT-VOICE-JITTER", "conceal frame")
        return ByteArray(frameBytes)
    }
}
