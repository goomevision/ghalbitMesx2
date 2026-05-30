package com.ghalbitnet.meshx2.call

import android.util.Log
import kotlin.math.abs

enum class VadDecision {
    SPEECH,
    SILENCE,
    NOISE,
    UNCERTAIN
}

class SimpleVadEngine {
    private var noiseFloor = 0.02

    fun classify(frame: VoiceFrame): VadDecision {
        val ratio = frame.energy / (noiseFloor.coerceAtLeast(0.01))
        val decision =
            when {
                ratio >= 2.4 -> VadDecision.SPEECH
                ratio <= 0.9 -> VadDecision.SILENCE
                abs(ratio - 1.0) < 0.3 -> VadDecision.NOISE
                else -> VadDecision.UNCERTAIN
            }
        noiseFloor = (noiseFloor * 0.92) + (frame.energy * 0.08)
        when (decision) {
            VadDecision.SPEECH -> Log.d("GHALBIT-VAD", "speech")
            VadDecision.SILENCE -> Log.d("GHALBIT-VAD", "silence skipped")
            VadDecision.NOISE -> Log.d("GHALBIT-VAD", "noise skipped")
            VadDecision.UNCERTAIN -> Log.d("GHALBIT-VAD", "uncertain compressed")
        }
        Log.d("GHALBIT-VAD", "noiseFloor=$noiseFloor")
        Log.d("GHALBIT-VAD", "speechRatio=$ratio")
        return decision
    }
}
