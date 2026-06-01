package com.ghalbitnet.meshx2.diagnostics.audio

import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

data class AudioLoopbackStats(
    val inputFrames: Int,
    val outputFrames: Int,
    val latencyMs: Long,
    val ok: Boolean
)

object AudioLoopbackAnalyzer {
    fun runInternalLoopback(sampleRate: Int = 16_000, durationMs: Int = 300): AudioLoopbackStats {
        Log.d("GHALBIT-LOOPBACK", "START sampleRate=$sampleRate durationMs=$durationMs")
        val frames = (sampleRate * durationMs / 1000).coerceAtLeast(1)
        val generated = ShortArray(frames)
        for (i in generated.indices) {
            generated[i] = (sin(2.0 * PI * 660.0 * i / sampleRate) * 9000).toInt().toShort()
        }
        val startNs = System.nanoTime()
        val stats = AudioInputAnalyzer.analyze(generated, generated.size)
        val endNs = System.nanoTime()
        val latencyMs = ((endNs - startNs) / 1_000_000L).coerceAtLeast(1L)
        val ok = stats.rms > 100.0 && stats.peak > 500
        Log.d(
            "GHALBIT-LOOPBACK",
            "INPUT_FRAMES=${stats.frames} OUTPUT_FRAMES=$frames LATENCY_MS=$latencyMs RESULT=$ok"
        )
        return AudioLoopbackStats(
            inputFrames = stats.frames,
            outputFrames = frames,
            latencyMs = latencyMs,
            ok = ok
        )
    }
}

