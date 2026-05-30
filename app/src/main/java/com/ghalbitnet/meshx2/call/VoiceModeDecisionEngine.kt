package com.ghalbitnet.meshx2.call

import android.util.Log

object VoiceModeDecisionEngine {
    fun decide(current: AdaptiveVoiceMode, snapshot: VoiceQualitySnapshot): AdaptiveVoiceMode {
        val next =
            when (snapshot.score) {
                VoiceQualityScore.EXCELLENT,
                VoiceQualityScore.GOOD -> AdaptiveVoiceMode.LIVE_VOICE
                VoiceQualityScore.DEGRADED -> if (snapshot.queueDelayMs >= 1_500L) AdaptiveVoiceMode.VOICE_CAPACITOR else AdaptiveVoiceMode.BUFFERED_VOICE
                VoiceQualityScore.CRITICAL -> AdaptiveVoiceMode.AI_RECONSTRUCTED_SPEECH
                VoiceQualityScore.LOST -> AdaptiveVoiceMode.PTT_STORE_FORWARD
            }
        if (next != current) {
            Log.d("GHALBIT-VOICE-MODE", "auto switch from=$current to=$next")
        } else {
            Log.d("GHALBIT-VOICE-MODE", "hysteresis hold")
        }
        return next
    }
}
