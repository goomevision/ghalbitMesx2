package com.ghalbitnet.meshx2.core.utils

import android.content.Context
import android.os.SystemClock
import android.widget.Toast

object UiFeedbackManager {
    private const val MIN_REPEAT_INTERVAL_MS = 1800L

    private var lastToastMessage: String = ""
    private var lastToastAt: Long = 0L
    private var activeToast: Toast? = null

    @Synchronized
    fun showToast(
        context: Context,
        message: String,
        duration: Int = Toast.LENGTH_SHORT
    ) {
        val now = SystemClock.elapsedRealtime()
        if (
            message == lastToastMessage &&
            now - lastToastAt < MIN_REPEAT_INTERVAL_MS
        ) {
            return
        }

        lastToastMessage = message
        lastToastAt = now

        activeToast?.cancel()
        activeToast =
            Toast.makeText(context.applicationContext, message, duration).also {
                it.show()
            }
    }
}
