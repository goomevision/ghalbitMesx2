package com.ghalbitnet.meshx2.core.network

import android.content.Context
import android.net.wifi.WifiManager

object HotspotSystemConfigManager {

    data class Snapshot(
        val ssid: String?,
        val password: String?,
        val readable: Boolean
    )

    fun snapshot(context: Context): Snapshot {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return Snapshot(null, null, false)

        readSoftApConfiguration(wifiManager)?.let { return it }
        readLegacyApConfiguration(wifiManager)?.let { return it }
        return Snapshot(null, null, false)
    }

    private fun readSoftApConfiguration(wifiManager: WifiManager): Snapshot? {
        return runCatching {
            val method = wifiManager.javaClass.methods.firstOrNull { it.name == "getSoftApConfiguration" }
                ?: return null
            val config = method.invoke(wifiManager) ?: return null
            val ssid = config.javaClass.methods.firstOrNull { it.name == "getSsid" }?.invoke(config) as? String
            val password =
                config.javaClass.methods.firstOrNull { it.name == "getPassphrase" }?.invoke(config) as? String
            Snapshot(ssid, password, !ssid.isNullOrBlank() && !password.isNullOrBlank())
        }.getOrNull()
    }

    private fun readLegacyApConfiguration(wifiManager: WifiManager): Snapshot? {
        return runCatching {
            val method = wifiManager.javaClass.methods.firstOrNull { it.name == "getWifiApConfiguration" }
                ?: return null
            val config = method.invoke(wifiManager) ?: return null
            val ssid = config.javaClass.getField("SSID").get(config) as? String
            val password = config.javaClass.getField("preSharedKey").get(config) as? String
            Snapshot(ssid, password, !ssid.isNullOrBlank() && !password.isNullOrBlank())
        }.getOrNull()
    }
}
