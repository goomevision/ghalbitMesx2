package com.ghalbitnet.meshx2.chat

import android.view.View
import android.util.Log

object ChatSystemAnimator {
    fun apply(view: View, lifetime: EventLifetime) {
        when (lifetime) {
            EventLifetime.TRANSIENT -> {
                view.alpha = 0.88f
                Log.d("GHALBIT-SYSTEM-EVENT", "transient style applied")
            }
            EventLifetime.STICKY -> view.alpha = 0.96f
            EventLifetime.CRITICAL -> view.alpha = 1f
        }
    }
}
