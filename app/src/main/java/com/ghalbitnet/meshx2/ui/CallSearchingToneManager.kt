package com.ghalbitnet.meshx2.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

class CallSearchingToneManager {
    private val handler = Handler(Looper.getMainLooper())
    private var generator: ToneGenerator? = null
    private var active = false
    private val pulse =
        object : Runnable {
            override fun run() {
                if (!active) return
                generator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 120)
                handler.postDelayed(this, 3200L)
            }
        }

    fun start() {
        if (active) return
        active = true
        if (generator == null) {
            generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 26)
        }
        pulse.run()
    }

    fun stop() {
        active = false
        handler.removeCallbacks(pulse)
        generator?.stopTone()
    }

    fun stopAndRelease() {
        stop()
        generator?.release()
        generator = null
    }
}
