package com.ghalbitnet.meshx2.diagnostics.audio

import kotlin.math.abs
import kotlin.math.sqrt

data class AudioInputStats(
    val rms: Double,
    val peak: Int,
    val noiseFloor: Double,
    val speechDetected: Boolean,
    val clippingDetected: Boolean,
    val frames: Int
)

object AudioInputAnalyzer {
    fun analyze(samples: ShortArray, count: Int): AudioInputStats {
        if (count <= 0) {
            return AudioInputStats(
                rms = 0.0,
                peak = 0,
                noiseFloor = 0.0,
                speechDetected = false,
                clippingDetected = false,
                frames = 0
            )
        }
        var sumSq = 0.0
        var peak = 0
        var nearClipping = 0
        for (i in 0 until count) {
            val sample = samples[i].toInt()
            val absSample = abs(sample)
            sumSq += sample * sample.toDouble()
            if (absSample > peak) peak = absSample
            if (absSample >= 32000) nearClipping++
        }
        val rms = sqrt(sumSq / count)
        val noiseFloor = rms * 0.35
        val speechDetected = rms > 900.0
        val clippingDetected = nearClipping > count / 50
        return AudioInputStats(
            rms = rms,
            peak = peak,
            noiseFloor = noiseFloor,
            speechDetected = speechDetected,
            clippingDetected = clippingDetected,
            frames = count
        )
    }
}

