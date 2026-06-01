package com.ghalbitnet.meshx2.diagnostics.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

data class AudioOutputStats(
    val writtenFrames: Int,
    val underrunLike: Boolean,
    val stallDetected: Boolean
)

object AudioOutputAnalyzer {
    fun playTone(sampleRate: Int, frequencyHz: Double, durationMs: Int): AudioOutputStats {
        val frameCount = (sampleRate * (durationMs / 1000.0)).toInt().coerceAtLeast(1)
        val tone = ShortArray(frameCount)
        for (i in tone.indices) {
            val v = sin(2.0 * PI * frequencyHz * i / sampleRate)
            tone[i] = (v * 10000).toInt().toShort()
        }
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(frameCount * 2)
        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            minBuffer,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        return try {
            Log.d("GHALBIT-AUDIO-OUT", "START freq=$frequencyHz durationMs=$durationMs")
            track.play()
            val written = track.write(tone, 0, tone.size, AudioTrack.WRITE_BLOCKING)
            Log.d("GHALBIT-AUDIO-OUT", "WRITE freq=$frequencyHz frames=$written")
            val underrunLike = written < tone.size
            val stall = track.playState != AudioTrack.PLAYSTATE_PLAYING
            if (underrunLike) Log.w("GHALBIT-AUDIO-OUT", "UNDERRUN freq=$frequencyHz")
            if (stall) Log.w("GHALBIT-AUDIO-OUT", "STALL freq=$frequencyHz")
            AudioOutputStats(
                writtenFrames = written.coerceAtLeast(0),
                underrunLike = underrunLike,
                stallDetected = stall
            )
        } finally {
            track.stop()
            track.release()
            Log.d("GHALBIT-AUDIO-OUT", "STOP freq=$frequencyHz")
        }
    }
}

