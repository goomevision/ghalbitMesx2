package com.ghalbitnet.meshx2.call

import android.util.Log
import java.util.TreeMap

class AudioPacketJitterBuffer(
    private val frameBytes: Int,
    private val targetFrames: Int = 2
) {
    private val packets = TreeMap<Int, ByteArray>()
    private var nextSequence = 0
    private var started = false
    private var silenceFramesBeforeStart = 0

    @Synchronized
    fun clear() {
        packets.clear()
        nextSequence = 0
        started = false
        silenceFramesBeforeStart = 0
    }

    @Synchronized
    fun offer(sequenceNumber: Int, audioData: ByteArray) {
        packets[sequenceNumber] = audioData
        if (!started && packets.size >= targetFrames) {
            nextSequence = packets.firstKey()
            started = true
            Log.d("GHALBIT-CALL-AUDIO-RX", "jitterStarted firstSeq=$nextSequence buffered=${packets.size}")
        }
        Log.d("GHALBIT-VOICE-PACKET", "received seq=$sequenceNumber")
        Log.d("GHALBIT-VOICE-JITTER", "buffer=${packets.size}")
    }

    @Synchronized
    fun pollFrame(): ByteArray {
        if (!started) {
            silenceFramesBeforeStart++
            if (silenceFramesBeforeStart == 1 || silenceFramesBeforeStart % 50 == 0) {
                Log.d("GHALBIT-CALL-AUDIO-RX", "waitingForFirstAudio buffered=${packets.size} target=$targetFrames")
            }
            return ByteArray(frameBytes)
        }

        val expected = nextSequence++
        val frame = packets.remove(expected)
        if (frame != null) {
            Log.d("GHALBIT-CALL-AUDIO-RX", "playFrame seq=$expected bytes=${frame.size}")
            return frame
        }

        Log.d("GHALBIT-VOICE-JITTER", "late dropped")
        Log.d("GHALBIT-VOICE-JITTER", "conceal frame")
        return ByteArray(frameBytes)
    }
}
