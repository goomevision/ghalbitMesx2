package com.ghalbitnet.meshx2.call

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build

data class MediaCapabilityReport(
    val videoRecommended: Boolean,
    val profile: String,
    val reason: String
)

object MediaCapabilityChecker {
    fun evaluate(context: Context, routeType: VoipRouteType): MediaCapabilityReport {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryClass = activityManager?.memoryClass ?: 0
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryLevel =
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it >= 0 }
                ?: 100

        val profile =
            when {
                memoryClass >= 256 && batteryLevel >= 60 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && routeType != VoipRouteType.FALLBACK_PTT -> "HIGH"
                memoryClass >= 192 && batteryLevel >= 40 && routeType != VoipRouteType.FALLBACK_PTT -> "MEDIUM"
                memoryClass >= 128 && batteryLevel >= 25 && routeType != VoipRouteType.FALLBACK_PTT -> "LOW"
                else -> "AUDIO_ONLY"
            }

        val recommended = profile != "AUDIO_ONLY"
        val reason = if (recommended) "Video $profile tersedia untuk perangkat ini." else "Video tidak disarankan, gunakan suara."
        return MediaCapabilityReport(recommended, profile, reason)
    }
}
