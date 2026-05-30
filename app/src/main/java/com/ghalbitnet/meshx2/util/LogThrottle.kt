package com.ghalbitnet.meshx2.util

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.settings.DeveloperModeManager
import java.util.concurrent.ConcurrentHashMap

object LogThrottle {
    private val lastLoggedAt = ConcurrentHashMap<String, Long>()

    fun shouldLog(key: String, windowMs: Long, context: Context? = null): Boolean {
        if (context != null && DeveloperModeManager.isEnabled(context)) {
            return true
        }
        val now = System.currentTimeMillis()
        val previous = lastLoggedAt[key]
        return if (previous == null || now - previous >= windowMs) {
            lastLoggedAt[key] = now
            true
        } else {
            false
        }
    }

    fun d(tag: String, key: String, message: String, windowMs: Long = 5_000L, context: Context? = null) {
        if (shouldLog(key, windowMs, context)) {
            Log.d(tag, message)
        }
    }

    fun i(tag: String, key: String, message: String, windowMs: Long = 5_000L, context: Context? = null) {
        if (shouldLog(key, windowMs, context)) {
            Log.i(tag, message)
        }
    }

    fun w(tag: String, key: String, message: String, windowMs: Long = 5_000L, context: Context? = null) {
        if (shouldLog(key, windowMs, context)) {
            Log.w(tag, message)
        }
    }
}
