package com.ghalbitnet.meshx2.call

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

object CallRingtoneManager {
    @Volatile
    private var activeCallId: String? = null
    @Volatile
    private var ringtone: Ringtone? = null
    @Volatile
    private var vibrator: Vibrator? = null

    fun startIncoming(context: Context, callId: String) {
        Log.d("GHALBIT-CALL-RING", "audit source=CallSessionActivity")
        if (activeCallId == callId && ringtone?.isPlaying == true) {
            Log.d("GHALBIT-CALL-RING", "already playing callId=$callId")
            return
        }
        if (activeCallId != null && activeCallId != callId) {
            Log.w("GHALBIT-CALL-RING", "duplicate source detected")
            stop("switch-call")
        }
        val ringtoneUri =
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return
        runCatching {
            ringtone = RingtoneManager.getRingtone(context.applicationContext, ringtoneUri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                }
                play()
            }
            vibrator = context.getSystemService(Vibrator::class.java)
            vibrator?.let { vib ->
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 350, 250, 350), 0))
                    } else {
                        @Suppress("DEPRECATION")
                        vib.vibrate(longArrayOf(0, 350, 250, 350), 0)
                    }
                }.onFailure {
                    Log.w("GHALBIT-CALL-RING", "vibrate skipped callId=$callId reason=${it.message}")
                }
            }
            activeCallId = callId
            Log.d("GHALBIT-CALL-RING", "start callId=$callId")
        }.onFailure {
            Log.e("GHALBIT-CALL-RING", "start failed callId=$callId", it)
        }
    }

    fun stop(reason: String) {
        runCatching { ringtone?.stop() }
        runCatching { vibrator?.cancel() }
        ringtone = null
        vibrator = null
        activeCallId = null
        Log.d("GHALBIT-CALL-RING", "stop reason=$reason")
        Log.d("GHALBIT-CALL-RING", "release ok")
    }

    fun stopIfCall(callId: String, reason: String) {
        if (activeCallId != null && activeCallId != callId) {
            Log.d("GHALBIT-CALL-RING", "ignored stop different callId=$callId")
            return
        }
        stop(reason)
    }

    fun isPlaying(): Boolean = ringtone?.isPlaying == true
}
