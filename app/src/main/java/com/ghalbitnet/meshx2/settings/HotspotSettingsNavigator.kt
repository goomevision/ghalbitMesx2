package com.ghalbitnet.meshx2.settings

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import com.ghalbitnet.meshx2.vpn.VpnLogManager

object HotspotSettingsNavigator {

    fun openHotspotSettings(activity: Activity): Boolean {
        val intents =
            listOf(
                Intent("android.settings.TETHER_SETTINGS"),
                Intent(Settings.ACTION_WIFI_SETTINGS),
                Intent(Settings.ACTION_WIRELESS_SETTINGS)
            )
        intents.forEach { intent ->
            val success =
                runCatching {
                    activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true
                }.getOrElse { false }
            if (success) {
                VpnLogManager.info(
                    "HOTSPOT_SETTINGS_OPENED",
                    "action=${intent.action.orEmpty()}"
                )
                return true
            }
        }
        return false
    }

    fun openForPasswordChange(activity: Activity): Boolean {
        val opened = openHotspotSettings(activity)
        if (opened) {
            VpnLogManager.info(
                "HOTSPOT_SETTINGS_OPENED_FOR_PASSWORD_CHANGE",
                "Provider diarahkan ke pengaturan hotspot untuk ganti password."
            )
        }
        return opened
    }
}
