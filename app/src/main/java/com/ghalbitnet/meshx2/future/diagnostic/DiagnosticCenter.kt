package com.ghalbitnet.meshx2.future.diagnostic

/**
 * DIAGNOSTIC CENTER
 *
 * Tahap sekarang:
 * - membuat status sederhana
 *
 * Masa depan:
 * - deteksi crash
 * - deteksi izin kurang
 * - deteksi node mati
 * - rekomendasi perbaikan otomatis
 */
object DiagnosticCenter {

    fun permissionStatus(
        hasLocation: Boolean,
        hasBluetooth: Boolean,
        hasNotification: Boolean
    ): String {

        val missing = mutableListOf<String>()

        if (!hasLocation) missing.add("LOCATION")
        if (!hasBluetooth) missing.add("BLUETOOTH")
        if (!hasNotification) missing.add("NOTIFICATION")

        return if (missing.isEmpty()) {
            "OK"
        } else {
            "MISSING: ${missing.joinToString(", ")}"
        }
    }

    fun networkHealth(
        nodeCount: Int,
        averageLatency: Long
    ): String {

        return when {
            nodeCount == 0 -> "NO NODE"
            averageLatency < 100 -> "GOOD"
            averageLatency < 300 -> "MEDIUM"
            else -> "WEAK"
        }
    }
}
