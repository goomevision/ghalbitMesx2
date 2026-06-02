package com.ghalbitnet.meshx2.call

import android.util.Log
import java.util.TreeMap

class AudioPacketJitterBuffer(
    private val frameBytes: Int,
    private val targetFrames: Int = 2
) {
    data class PollResult(
        val frame: ByteArray,
        val sequence: Int?,
        val realFrame: Boolean,
        val concealed: Boolean
    )

    private val packets = TreeMap<Int, ByteArray>()
    private var nextSequence = 0
    private var started = false
    private var silenceFramesBeforeStart = 0
    private var rxFrames = 0L
    private var playedFrames = 0L
    private var droppedFrames = 0L
    private var concealFrames = 0L
    private var lastSeqOffered = -1
    private var jitterAccumulator = 0L
    private var jitterSamples = 0L
    private var firstRxAtMs = 0L
    private var lastMetricsLogAtMs = 0L

    @Synchronized
    fun clear() {
        packets.clear()
        nextSequence = 0
        started = false
        silenceFramesBeforeStart = 0
        rxFrames = 0L
        playedFrames = 0L
        droppedFrames = 0L
        concealFrames = 0L
        lastSeqOffered = -1
        jitterAccumulator = 0L
        jitterSamples = 0L
        firstRxAtMs = 0L
        lastMetricsLogAtMs = 0L
    }

    @Synchronized
    fun offer(sequenceNumber: Int, audioData: ByteArray) {
        packets[sequenceNumber] = audioData
        rxFrames++
        if (firstRxAtMs == 0L) {
            firstRxAtMs = System.currentTimeMillis()
        }
        if (lastSeqOffered >= 0) {
            val gap = kotlin.math.abs(sequenceNumber - lastSeqOffered)
            jitterAccumulator += gap.toLong()
            jitterSamples++
        }
        lastSeqOffered = sequenceNumber
        if (!started && packets.size >= targetFrames) {
            nextSequence = packets.firstKey()
            started = true
            Log.d("GHALBIT-CALL-AUDIO-RX", "jitterStarted firstSeq=$nextSequence buffered=${packets.size}")
        }
        Log.d("GHALBIT-VOICE-PACKET", "received seq=$sequenceNumber")
        maybeLogMetrics()
    }

    @Synchronized
    fun pollFrame(): ByteArray = pollResult().frame

    @Synchronized
    fun pollResult(): PollResult {
        if (!started) {
            silenceFramesBeforeStart++
            if (silenceFramesBeforeStart == 1 || silenceFramesBeforeStart % 50 == 0) {
                Log.d("GHALBIT-CALL-AUDIO-RX", "waitingForFirstAudio buffered=${packets.size} target=$targetFrames")
            }
            return PollResult(ByteArray(frameBytes), sequence = null, realFrame = false, concealed = true)
        }

        val expected = nextSequence++
        val frame = packets.remove(expected)
        if (frame != null) {
            playedFrames++
            Log.d("GHALBIT-CALL-AUDIO-RX", "playFrame seq=$expected bytes=${frame.size}")
            maybeLogMetrics()
            return PollResult(frame, sequence = expected, realFrame = true, concealed = false)
        }

        droppedFrames++
        concealFrames++
        maybeLogMetrics()
        return PollResult(ByteArray(frameBytes), sequence = expected, realFrame = false, concealed = true)
    }

    @Synchronized
    fun metricsSnapshot(): AudioJitterMetrics {
        val now = System.currentTimeMillis()
        val jitterAvg = if (jitterSamples > 0) jitterAccumulator.toDouble() / jitterSamples.toDouble() else 0.0
        return AudioJitterMetrics(
            rxFrames = rxFrames,
            playedFrames = playedFrames,
            droppedFrames = droppedFrames,
            concealFrames = concealFrames,
            queuedFrames = packets.size,
            jitterAverage = jitterAvg,
            firstRxAtMs = firstRxAtMs,
            rxActiveForMs = if (firstRxAtMs > 0L) now - firstRxAtMs else 0L
        )
    }

    @Synchronized
    private fun maybeLogMetrics() {
        val now = System.currentTimeMillis()
        if (now - lastMetricsLogAtMs < 1000L) return
        lastMetricsLogAtMs = now
        val m = metricsSnapshot()
        Log.d(
            "GHALBIT-VOICE-METRICS",
            "rx=${m.rxFrames} play=${m.playedFrames} drop=${m.droppedFrames} conceal=${m.concealFrames} jitterAvg=${"%.2f".format(m.jitterAverage)} buffer=${m.queuedFrames}"
        )
    }

    data class AudioJitterMetrics(
        val rxFrames: Long,
        val playedFrames: Long,
        val droppedFrames: Long,
        val concealFrames: Long,
        val queuedFrames: Int,
        val jitterAverage: Double,
        val firstRxAtMs: Long,
        val rxActiveForMs: Long
    )
}
