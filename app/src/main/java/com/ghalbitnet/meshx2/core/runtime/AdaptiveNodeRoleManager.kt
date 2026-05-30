package com.ghalbitnet.meshx2.core.runtime

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.util.Log

object AdaptiveNodeRoleManager {
    private const val TAG = "GHALBIT-ROUTE"

    fun assess(context: Context, hotspotLikelyActive: Boolean = false): AdaptiveNodeRoleReport {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) (level * 100) / scale else 50
        val charging = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = connectivityManager?.activeNetworkInfo
        val networkConnected = activeNetwork?.isConnectedOrConnecting == true

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryClassMb = activityManager?.memoryClass ?: 128

        val role = when {
            charging && hotspotLikelyActive && memoryClassMb >= 192 -> AdaptiveNodeRole.GATEWAY_NODE
            charging && memoryClassMb >= 160 && networkConnected -> AdaptiveNodeRole.BACKUP_NODE
            batteryPercent >= 45 && networkConnected && memoryClassMb >= 128 -> AdaptiveNodeRole.RELAY_NODE
            else -> AdaptiveNodeRole.LIGHT_NODE
        }

        val reason = when (role) {
            AdaptiveNodeRole.GATEWAY_NODE -> "charging+hotspot+memory"
            AdaptiveNodeRole.BACKUP_NODE -> "charging+memory+network"
            AdaptiveNodeRole.RELAY_NODE -> "healthy battery and network"
            AdaptiveNodeRole.LIGHT_NODE -> "battery/network/memory constrained"
        }

        Log.d(TAG, "Adaptive role assessed as $role reason=$reason")
        return AdaptiveNodeRoleReport(
            role = role,
            batteryPercent = batteryPercent,
            charging = charging,
            hotspotLikelyActive = hotspotLikelyActive,
            memoryClassMb = memoryClassMb,
            networkConnected = networkConnected,
            reason = reason
        )
    }
}
