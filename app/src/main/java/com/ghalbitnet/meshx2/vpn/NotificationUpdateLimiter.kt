package com.ghalbitnet.meshx2.vpn

object NotificationUpdateLimiter {

    private const val MIN_UPDATE_INTERVAL_MS = 30_000L

    @Volatile
    private var lastNotificationUpdate: Long = 0L

    fun shouldUpdate(now: Long = System.currentTimeMillis(), force: Boolean = false): Boolean {
        if (force) {
            lastNotificationUpdate = now
            return true
        }
        if (now - lastNotificationUpdate < MIN_UPDATE_INTERVAL_MS) {
            return false
        }
        lastNotificationUpdate = now
        return true
    }

    fun reset() {
        lastNotificationUpdate = 0L
    }
}
