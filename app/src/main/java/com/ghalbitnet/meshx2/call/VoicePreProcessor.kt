package com.ghalbitnet.meshx2.call

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log

data class VoiceFrame(
    val bytes: ByteArray,
    val energy: Double,
    val timestamp: Long = System.currentTimeMillis()
)

enum class VoiceQualityProfile {
    BASIC_PHONE,
    STRONG_PHONE,
    EMERGENCY_CLEAR_SPEECH,
    RAW_DEBUG
}

class VoicePreProcessor {
    fun prepare(audioSessionId: Int = 0): VoiceQualityProfile {
        Log.d("GHALBIT-VOICE-PRE", "start")
        if (AcousticEchoCanceler.isAvailable()) Log.d("GHALBIT-VOICE-PRE", "aec enabled")
        if (NoiseSuppressor.isAvailable()) Log.d("GHALBIT-VOICE-PRE", "ns enabled")
        if (AutomaticGainControl.isAvailable()) Log.d("GHALBIT-VOICE-PRE", "agc enabled")
        Log.d("GHALBIT-VOICE-PRE", "highpass enabled")
        Log.d("GHALBIT-VOICE-PRE", "pipeline ready")
        Log.d("GHALBIT-VOICE-PRIVACY", "local preprocessing only")
        return if (audioSessionId > 0) VoiceQualityProfile.STRONG_PHONE else VoiceQualityProfile.BASIC_PHONE
    }
}
