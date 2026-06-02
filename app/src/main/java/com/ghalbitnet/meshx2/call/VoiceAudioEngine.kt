package com.ghalbitnet.meshx2.call

import android.content.Context
import android.media.AudioManager
import android.util.Log

class VoiceAudioEngine(
    private val context: Context,
    private val audioManager: AudioManager,
    private val duplexEngine: FullDuplexCallEngine,
    private val onEngineEvent: ((stage: String, details: String) -> Unit)? = null
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
            Log.w("GHALBIT-CALL-AUDIO-ENGINE", "FAIL stage=prepare reason=audio_focus")
            onEngineEvent?.invoke("fail", "stage=prepare reason=audio_focus")
            return false
        }
        Log.d("GHALBIT-CALL-AUDIO-ENGINE", "START speaker=$speakerEnabled")
        onEngineEvent?.invoke("start", "speaker=$speakerEnabled")
        return runCatching {
            duplexEngine.start()
            captureActive = true
            playbackActive = true
            Log.d("GHALBIT-CALL-AUDIO-ENGINE", "READY capture=$captureActive playback=$playbackActive")
            onEngineEvent?.invoke("ready", "capture=$captureActive playback=$playbackActive")
            Log.d("GHALBIT-VOICE-AUDIT", "socket=local-duplex")
            Log.d("GHALBIT-VOICE-AUDIT", "recorder=$captureActive")
            Log.d("GHALBIT-VOICE-AUDIT", "player=$playbackActive")
            Log.d("GHALBIT-AUDIO", "recorder started")
            Log.d("GHALBIT-AUDIO", "player started")
            true
        }.getOrElse { error ->
            captureActive = false
            playbackActive = false
            Log.e("GHALBIT-CALL-AUDIO-ENGINE", "FAIL stage=start reason=${error.message}", error)
            onEngineEvent?.invoke("fail", "stage=start reason=${error.message}")
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
        Log.d("GHALBIT-CALL-AUDIO-ENGINE", "STOP capture=$captureActive playback=$playbackActive")
        onEngineEvent?.invoke("stop", "capture=$captureActive playback=$playbackActive")
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
