package com.ghalbitnet.meshx2.call

import android.util.Log

object VoiceOutputMixer {
    fun fadeOriginalOut() {
        Log.d("GHALBIT-VOICE-MIX", "fade original out")
    }

    fun fadeTtsIn() {
        Log.d("GHALBIT-VOICE-MIX", "tts in")
    }

    fun restoreLive() {
        Log.d("GHALBIT-VOICE-MIX", "live restored")
    }
}
