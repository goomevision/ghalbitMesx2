package com.ghalbitnet.meshx2.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Build

object LowAnimationMode {
    fun enabled(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryClass = activityManager?.memoryClass ?: 0
        val lowRam = activityManager?.isLowRamDevice ?: false
        return lowRam || memoryClass in 1..160 || Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1
    }
}
