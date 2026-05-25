package com.ghalbitnet.meshx2.core.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.ghalbitnet.meshx2.core.config.MeshConfigCenter

object DeviceCapability {

    fun isLowEndDevice(context: Context): Boolean {
        return isLowRam(context) ||
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1
    }

    fun isLowRam(context: Context): Boolean {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        return activityManager.isLowRamDevice
    }

    fun recommendedHeartbeatInterval(context: Context): Long {
        return if (isLowEndDevice(context)) {
            MeshConfigCenter.HEARTBEAT_LOW_POWER_MS
        } else {
            MeshConfigCenter.HEARTBEAT_NORMAL_MS
        }
    }

    fun recommendedDiscoveryInterval(context: Context): Long {
        return if (isLowEndDevice(context)) {
            MeshConfigCenter.DISCOVERY_LOW_POWER_INTERVAL_MS
        } else {
            MeshConfigCenter.DISCOVERY_NORMAL_INTERVAL_MS
        }
    }
}
