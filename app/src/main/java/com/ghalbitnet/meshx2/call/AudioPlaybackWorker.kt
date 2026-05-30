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

    fun start() {
        if (job?.isActive == true) return
        Log.d("GHALBIT-AUDIO-MODE", "jitterBuffer=${frameMs * 6}")

        val minBuffer =
            AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
        val track =
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
                .setBufferSizeInBytes(maxOf(minBuffer, frameBytes * 8))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

        audioTrack = track
        track.play()

        job =
            scope.launch {
                while (isActive) {
                    val frame = jitterBuffer.pollFrame()
                    track.write(frame, 0, frame.size)
                    delay(frameMs.toLong())
                }
            }
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
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
