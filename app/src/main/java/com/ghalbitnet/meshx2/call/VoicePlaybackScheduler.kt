package com.ghalbitnet.meshx2.call

import android.util.Log

class VoicePlaybackScheduler {
    fun playbackDelayFor(mode: AdaptiveVoiceMode): Long {
        val delay =
            when (mode) {
                AdaptiveVoiceMode.LIVE_VOICE -> 300L
                AdaptiveVoiceMode.BUFFERED_VOICE -> 1_000L
                AdaptiveVoiceMode.VOICE_CAPACITOR -> 3_000L
                AdaptiveVoiceMode.PTT_STORE_FORWARD -> 8_000L
                AdaptiveVoiceMode.AI_RECONSTRUCTED_SPEECH -> 1_500L
            }
        Log.d("GHALBIT-VOICE-DELAY", "set ms=$delay")
        return delay
    }
}
