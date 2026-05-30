package com.ghalbitnet.meshx2.call

import android.util.Log

data class CodecProfile(
    val codecName: String,
    val bitrateKbps: Int,
    val sampleRate: Int,
    val frameMs: Int,
    val dtxEnabled: Boolean,
    val fecEnabled: Boolean
)

interface CodecAdapter {
    fun select(mode: AdaptiveVoiceMode): CodecProfile
}

class SpeechOptimizedCodecAdapter : CodecAdapter {
    override fun select(mode: AdaptiveVoiceMode): CodecProfile {
        val profile =
            when (mode) {
                AdaptiveVoiceMode.LIVE_VOICE -> CodecProfile("PCM_FALLBACK", 24, 16_000, 20, false, false)
                AdaptiveVoiceMode.BUFFERED_VOICE -> CodecProfile("PCM_FALLBACK", 16, 16_000, 20, true, false)
                AdaptiveVoiceMode.VOICE_CAPACITOR -> CodecProfile("PCM_FALLBACK", 8, 8_000, 40, true, true)
                AdaptiveVoiceMode.PTT_STORE_FORWARD -> CodecProfile("PCM_FALLBACK", 8, 8_000, 40, true, true)
                AdaptiveVoiceMode.AI_RECONSTRUCTED_SPEECH -> CodecProfile("TEXT_ONLY", 1, 8_000, 20, true, true)
            }
        Log.d("GHALBIT-CODEC", "selected=${profile.codecName}")
        Log.d("GHALBIT-CODEC", "bitrate=${profile.bitrateKbps}")
        if (profile.dtxEnabled) Log.d("GHALBIT-CODEC", "dtx enabled")
        if (profile.fecEnabled) Log.d("GHALBIT-CODEC", "fec enabled")
        Log.d("GHALBIT-CODEC", "speech optimized")
        return profile
    }
}
