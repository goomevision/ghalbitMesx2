package com.ghalbitnet.meshx2.ui

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

object ActionDebounceManager {
    private val lastActionAt = ConcurrentHashMap<String, Long>()

    fun allow(action: String, runtimeBusy: Boolean, cooldownMs: Long = 900L): Boolean {
        val now = SystemClock.elapsedRealtime()
        val lastAt = lastActionAt[action] ?: 0L
        val allowed = !runtimeBusy && (now - lastAt >= cooldownMs)
        Log.d("GHALBIT-ACTION-LOCK", "action=$action runtimeBusy=$runtimeBusy allowed=$allowed cooldownMs=$cooldownMs")
        if (allowed) {
            lastActionAt[action] = now
        }
        return allowed
    }
}
