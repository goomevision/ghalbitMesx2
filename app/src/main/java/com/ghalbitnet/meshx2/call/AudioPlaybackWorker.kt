package com.ghalbitnet.meshx2.call

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioPlaybackWorker(
    private val jitterBuffer: AudioPacketJitterBuffer,
    private val sampleRate: Int = 8000,
    private val frameMs: Int = 20,
    private val frameBytes: Int
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var audioTrack: AudioTrack? = null
    private var job: Job? = null
    @Volatile private var safeMode: Boolean = false

    fun start() {
        if (job?.isActive == true) return
        Log.d("GHALBIT-AUDIO-MODE", "jitterBuffer=${frameMs * 2}")

        val minBuffer =
            AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
        if (minBuffer <= 0) {
            Log.e("GHALBIT-AUDIO-PLAYBACK", "minBuffer invalid=$minBuffer sampleRate=$sampleRate")
            return
        }
        val bufferSize = maxOf(minBuffer, frameBytes * 8)
        val track =
            runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setLegacyStreamType(AudioManager.STREAM_VOICE_CALL)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            }.getOrElse { error ->
                Log.e("GHALBIT-AUDIO-PLAYBACK", "track create failed", error)
                return
            }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            Log.e("GHALBIT-AUDIO-PLAYBACK", "track not initialized state=${track.state}")
            runCatching { track.release() }
            return
        }

        audioTrack = track
        runCatching { track.play() }
            .onFailure {
                Log.e("GHALBIT-AUDIO-PLAYBACK", "play failed", it)
                runCatching { track.release() }
                audioTrack = null
                return
            }
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
            Log.e("GHALBIT-AUDIO-PLAYBACK", "not playing state=${track.playState}")
            runCatching { track.release() }
            audioTrack = null
            return
        }
        Log.d("GHALBIT-AUDIO-PLAYBACK", "started bufferSize=$bufferSize frameBytes=$frameBytes")

        job =
            scope.launch {
                var writeCount = 0
                var zeroOrShortWrites = 0
                while (isActive) {
                    val frame = jitterBuffer.pollFrame()
                    val written = track.write(frame, 0, frame.size)
                    writeCount++
                    if (written != frame.size) {
                        zeroOrShortWrites++
                        Log.w("GHALBIT-AUDIO-PLAYBACK", "shortWrite written=$written expected=${frame.size} count=$zeroOrShortWrites")
                    } else if (writeCount == 1 || writeCount % 50 == 0) {
                        Log.d("GHALBIT-AUDIO-PLAYBACK", "frame written=$written count=$writeCount")
                    }
                    delay(if (safeMode) 10L else frameMs.toLong())
                }
            }
    }

    fun setSafeMode(enabled: Boolean) {
        safeMode = enabled
        Log.d("GHALBIT-CALL-AUDIO", "safePlayback=$enabled")
    }

    fun stop() {
        job?.cancel()
        job = null
        try {
            audioTrack?.pause()
        } catch (_: Exception) {
        }
        try {
            audioTrack?.flush()
        } catch (_: Exception) {
        }
        try {
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
        Log.d("GHALBIT-AUDIO-PLAYBACK", "stopped")
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
