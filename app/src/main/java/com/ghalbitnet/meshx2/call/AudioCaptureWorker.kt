package com.ghalbitnet.meshx2.call

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioCaptureWorker(
    private val sampleRate: Int = 8000,
    private val frameMs: Int = 20,
    private val onFrame: (ByteArray) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var audioRecord: AudioRecord? = null
    private var job: Job? = null

    val frameBytes: Int
        get() = (sampleRate * frameMs / 1000) * 2

    fun start() {
        if (job?.isActive == true) return
        Log.d("GHALBIT-AUDIO-MODE", "low bandwidth")
        Log.d("GHALBIT-AUDIO-MODE", "sampleRate=$sampleRate")
        Log.d("GHALBIT-AUDIO-MODE", "frameMs=$frameMs")

        val minBuffer =
            AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
        if (minBuffer <= 0) {
            Log.e("GHALBIT-AUDIO-CAPTURE", "minBuffer invalid=$minBuffer sampleRate=$sampleRate")
            return
        }
        val bufferSize = maxOf(minBuffer, frameBytes * 4)
        val recorder = createRecorder(bufferSize)
        if (recorder == null) {
            Log.e("GHALBIT-AUDIO-CAPTURE", "recorder create failed")
            return
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("GHALBIT-AUDIO-CAPTURE", "recorder not initialized state=${recorder.state}")
            runCatching { recorder.release() }
            return
        }

        audioRecord = recorder
        runCatching { recorder.startRecording() }
            .onFailure {
                Log.e("GHALBIT-AUDIO-CAPTURE", "startRecording failed", it)
                runCatching { recorder.release() }
                audioRecord = null
                return
            }
        if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            Log.e("GHALBIT-AUDIO-CAPTURE", "not recording state=${recorder.recordingState}")
            runCatching { recorder.release() }
            audioRecord = null
            return
        }
        Log.d("GHALBIT-AUDIO-CAPTURE", "started bufferSize=$bufferSize source=VOICE_COMMUNICATION_OR_MIC")

        job =
            scope.launch {
                val frame = ByteArray(frameBytes)
                var frameCount = 0
                while (isActive) {
                    val read = recorder.read(frame, 0, frame.size)
                    if (read > 0) {
                        frameCount++
                        if (frameCount == 1 || frameCount % 50 == 0) {
                            Log.d("GHALBIT-AUDIO-CAPTURE", "frame read=$read count=$frameCount")
                        }
                        onFrame(frame.copyOf(read))
                    } else {
                        Log.w("GHALBIT-CALL-RTC", "capture read=$read")
                    }
                }
            }
    }

    private fun createRecorder(bufferSize: Int): AudioRecord? {
        val sources = listOf(MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.MIC)
        sources.forEach { source ->
            val recorder =
                runCatching {
                    AudioRecord(
                        source,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )
                }.getOrNull()
            if (recorder?.state == AudioRecord.STATE_INITIALIZED) {
                Log.d("GHALBIT-AUDIO-CAPTURE", "recorder initialized source=$source")
                return recorder
            }
            runCatching { recorder?.release() }
            Log.w("GHALBIT-AUDIO-CAPTURE", "recorder source failed source=$source")
        }
        return null
    }

    fun stop() {
        job?.cancel()
        job = null
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
        Log.d("GHALBIT-AUDIO-CAPTURE", "stopped")
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
