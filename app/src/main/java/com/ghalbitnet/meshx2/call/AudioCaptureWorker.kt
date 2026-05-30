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
        val bufferSize = maxOf(minBuffer, frameBytes * 4)
        val recorder =
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

        audioRecord = recorder
        recorder.startRecording()

        job =
            scope.launch {
                val frame = ByteArray(frameBytes)
                while (isActive) {
                    val read = recorder.read(frame, 0, frame.size)
                    if (read > 0) {
                        onFrame(frame.copyOf(read))
                    } else {
                        Log.w("GHALBIT-CALL-RTC", "capture read=$read")
                    }
                }
            }
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
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
