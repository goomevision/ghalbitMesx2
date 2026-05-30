package com.ghalbitnet.meshx2.call

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class AiVoicePlaybackEngine(private val context: Context) {
    private var tts: TextToSpeech? = null

    fun speak(packet: AiTranscriptPacket) {
        ensureReady()
        if (packet.confidence < 0.4f) {
            Log.w("GHALBIT-AI-BRIDGE", "low confidence")
            Log.w("GHALBIT-AI-VOICE", "low confidence warning")
            return
        }
        Log.d("GHALBIT-TTS", "speak seq=${packet.sequenceNumber}")
        Log.d("GHALBIT-TTS", "language=${packet.language}")
        tts?.speak(packet.text, TextToSpeech.QUEUE_ADD, null, "ghalbit-tts-${packet.sequenceNumber}")
        Log.d("GHALBIT-AI-VOICE", "transcript displayed")
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }

    private fun ensureReady() {
        if (tts != null) return
        Log.d("GHALBIT-AI-VOICE", "neutral voice selected")
        Log.d("GHALBIT-AI-BRIDGE", "neutral voice")
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("id", "ID")
            }
        }
    }
}
