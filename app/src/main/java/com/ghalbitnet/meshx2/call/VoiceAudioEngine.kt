package com.ghalbitnet.meshx2.call

import android.content.Context
import android.media.AudioManager
import android.util.Log

class VoiceAudioEngine(
    private val context: Context,
    private val audioManager: AudioManager,
    private val duplexEngine: FullDuplexCallEngine
) {
    private var audioFocusGranted = false
    private var captureActive = false
    private var playbackActive = false

    fun prepare(speakerEnabled: Boolean): Boolean {
        val focusResult =
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
        audioFocusGranted = focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.d("GHALBIT-VOICE-AUDIT", "audio focus=$audioFocusGranted")
        if (!audioFocusGranted) {
            Log.w("GHALBIT-AUDIO", "focus failed")
            return false
        }
        Log.d("GHALBIT-AUDIO", "focus granted")
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = speakerEnabled
        Log.d("GHALBIT-AUDIO", "route ${if (speakerEnabled) "speaker" else "earpiece"}")
        return true
    }

    fun start(speakerEnabled: Boolean): Boolean {
        if (!audioFocusGranted && !prepare(speakerEnabled)) {
            return false
        }
        return runCatching {
            duplexEngine.start()
            captureActive = true
            playbackActive = true
            Log.d("GHALBIT-VOICE-AUDIT", "socket=local-duplex")
            Log.d("GHALBIT-VOICE-AUDIT", "recorder=$captureActive")
            Log.d("GHALBIT-VOICE-AUDIT", "player=$playbackActive")
            Log.d("GHALBIT-AUDIO", "recorder started")
            Log.d("GHALBIT-AUDIO", "player started")
            true
        }.getOrElse { error ->
            captureActive = false
            playbackActive = false
            Log.e("GHALBIT-AUDIO", "recorder failed", error)
            Log.e("GHALBIT-AUDIO", "player failed", error)
            false
        }
    }

    fun stop() {
        captureActive = false
        playbackActive = false
        runCatching { duplexEngine.stop() }
        if (audioFocusGranted) {
            runCatching { audioManager.abandonAudioFocus(null) }
            audioFocusGranted = false
        }
        Log.d("GHALBIT-AUDIO", "stopped")
    }

    fun release() {
        stop()
        runCatching { duplexEngine.release() }
        Log.d("GHALBIT-AUDIO", "released")
    }

    fun isCaptureActive(): Boolean = captureActive

    fun isPlaybackActive(): Boolean = playbackActive
}
