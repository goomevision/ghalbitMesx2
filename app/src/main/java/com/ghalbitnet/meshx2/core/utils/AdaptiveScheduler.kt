package com.ghalbitnet.meshx2.core.utils

import android.os.SystemClock

object AdaptiveScheduler {

    private var lastRun = 0L

    fun shouldRun(
        interval: Long
    ): Boolean {

        val now =
            SystemClock.elapsedRealtime()

        if (now - lastRun >= interval) {

            lastRun = now
            return true
        }

        return false
    }
}
