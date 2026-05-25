package com.ghalbitnet.meshx2.access

object NetworkAccessPolicy {

    private const val CURRENT_APP_VERSION = "1.0.1"

    enum class AuthStatus {
        AUTH_PENDING,
        AUTHORIZED,
        UNAUTHORIZED,
        EXPIRED,
        BLOCKED,
        UNKNOWN_NO_HELLO_AUTH,
        UNKNOWN_DEVICE
    }

    const val ACCESS_TOKEN_TTL_MS = 10 * 60 * 1000L
    const val TIMESTAMP_TOLERANCE_MS = 90 * 1000L
    const val DEFAULT_MESH_SOCKET_PORT = 56565

    fun isTimestampAccepted(timestamp: Long, now: Long = System.currentTimeMillis()): Boolean {
        return kotlin.math.abs(now - timestamp) <= TIMESTAMP_TOLERANCE_MS
    }

    fun isAppVersionAccepted(appVersion: String): Boolean {
        if (appVersion.isBlank()) return false
        return appVersion == CURRENT_APP_VERSION || appVersion.substringBefore('.') == CURRENT_APP_VERSION.substringBefore('.')
    }
}
