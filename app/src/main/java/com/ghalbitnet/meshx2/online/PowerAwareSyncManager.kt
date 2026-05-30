package com.ghalbitnet.meshx2.online

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log

object PowerAwareSyncManager {
    data class Snapshot(
        val level: Int,
        val charging: Boolean,
        val lowPowerMode: Boolean,
        val heartbeatIntervalMs: Long,
        val inboxPollIntervalMs: Long
    )

    fun snapshot(context: Context): Snapshot {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) ?: 100
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val lowPower = !charging && level <= 25
        if (lowPower) {
            Log.d("GHALBIT-POWER", "low power mode")
        }
        return if (lowPower) {
            Snapshot(level, charging, true, heartbeatIntervalMs = 90_000L, inboxPollIntervalMs = 20_000L)
        } else {
            Snapshot(level, charging, false, heartbeatIntervalMs = 45_000L, inboxPollIntervalMs = 8_000L)
        }
    }
}
