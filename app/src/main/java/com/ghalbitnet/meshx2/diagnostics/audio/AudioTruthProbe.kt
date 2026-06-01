package com.ghalbitnet.meshx2.diagnostics.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat

data class AudioTruthReport(
    val healthScore: Int,
    val micFrames: Int,
    val rms: Double,
    val peak: Int,
    val noiseFloor: Double,
    val speechDetected: Boolean,
    val clippingDetected: Boolean,
    val tone440Played: Boolean,
    val tone1000Played: Boolean,
    val outputUnderrun: Boolean,
    val outputStall: Boolean,
    val loopbackOk: Boolean,
    val loopbackLatencyMs: Long,
    val notes: List<String>
)

object AudioTruthProbe {
    fun run(context: Context): AudioTruthReport {
        val notes = mutableListOf<String>()
        val micPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!micPerm) {
            notes += "RECORD_AUDIO permission missing"
        }
        val inputStats = if (micPerm) captureMic() else AudioInputStats(0.0, 0, 0.0, false, false, 0)
        val tone440 = runCatching {
            Log.d("GHALBIT-TONE-TEST", "START freq=440")
            AudioOutputAnalyzer.playTone(16_000, 440.0, 350)
        }.getOrElse {
            notes += "tone440 failed: ${it.javaClass.simpleName}"
            AudioOutputStats(0, underrunLike = true, stallDetected = true)
        }.also { Log.d("GHALBIT-TONE-TEST", "PLAYED freq=440 frames=${it.writtenFrames}") }

        val tone1k = runCatching {
            Log.d("GHALBIT-TONE-TEST", "START freq=1000")
            AudioOutputAnalyzer.playTone(16_000, 1000.0, 350)
        }.getOrElse {
            notes += "tone1000 failed: ${it.javaClass.simpleName}"
            AudioOutputStats(0, underrunLike = true, stallDetected = true)
        }.also { Log.d("GHALBIT-TONE-TEST", "PLAYED freq=1000 frames=${it.writtenFrames}") }

        Log.d("GHALBIT-TONE-TEST", "STOP")
        val loop = AudioLoopbackAnalyzer.runInternalLoopback()
        val health = calculateHealth(inputStats, tone440, tone1k, loop)
        val report = AudioTruthReport(
            healthScore = health,
            micFrames = inputStats.frames,
            rms = inputStats.rms,
            peak = inputStats.peak,
            noiseFloor = inputStats.noiseFloor,
            speechDetected = inputStats.speechDetected,
            clippingDetected = inputStats.clippingDetected,
            tone440Played = tone440.writtenFrames > 0,
            tone1000Played = tone1k.writtenFrames > 0,
            outputUnderrun = tone440.underrunLike || tone1k.underrunLike,
            outputStall = tone440.stallDetected || tone1k.stallDetected,
            loopbackOk = loop.ok,
            loopbackLatencyMs = loop.latencyMs,
            notes = notes
        )
        Log.d("GHALBIT-AUDIO-TRUTH", "healthScore=${report.healthScore} notes=${report.notes.joinToString()}")
        return report
    }

    private fun captureMic(): AudioInputStats {
        val sampleRate = 16_000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer
        )
        val buffer = ShortArray(minBuffer / 2)
        return try {
            Log.d("GHALBIT-AUDIO-IN", "START rate=$sampleRate")
            record.startRecording()
            var totalRead = 0
            var sumRms = 0.0
            var bestPeak = 0
            var speech = false
            var clip = false
            repeat(12) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val st = AudioInputAnalyzer.analyze(buffer, read)
                    totalRead += st.frames
                    sumRms += st.rms
                    bestPeak = maxOf(bestPeak, st.peak)
                    speech = speech || st.speechDetected
                    clip = clip || st.clippingDetected
                    Log.d(
                        "GHALBIT-AUDIO-IN",
                        "RMS=${"%.2f".format(st.rms)} PEAK=${st.peak} NOISE=${"%.2f".format(st.noiseFloor)}"
                    )
                    if (st.speechDetected) Log.d("GHALBIT-AUDIO-IN", "SPEECH_DETECTED")
                    if (st.clippingDetected) Log.w("GHALBIT-AUDIO-IN", "CLIPPING")
                }
            }
            val avgRms = if (totalRead == 0) 0.0 else sumRms / 12.0
            AudioInputStats(
                rms = avgRms,
                peak = bestPeak,
                noiseFloor = avgRms * 0.35,
                speechDetected = speech,
                clippingDetected = clip,
                frames = totalRead
            )
        } finally {
            runCatching { record.stop() }
            record.release()
            Log.d("GHALBIT-AUDIO-IN", "STOP")
        }
    }

    private fun calculateHealth(
        input: AudioInputStats,
        tone440: AudioOutputStats,
        tone1k: AudioOutputStats,
        loopback: AudioLoopbackStats
    ): Int {
        var score = 100
        if (input.frames <= 0) score -= 35
        if (input.rms < 60.0) score -= 15
        if (input.clippingDetected) score -= 10
        if (tone440.writtenFrames <= 0 || tone1k.writtenFrames <= 0) score -= 20
        if (tone440.underrunLike || tone1k.underrunLike) score -= 10
        if (tone440.stallDetected || tone1k.stallDetected) score -= 10
        if (!loopback.ok) score -= 10
        if (loopback.latencyMs > 120) score -= 5
        return score.coerceIn(0, 100)
    }
}
