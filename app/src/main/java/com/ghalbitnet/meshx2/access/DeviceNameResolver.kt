package com.ghalbitnet.meshx2.access

import android.content.Context
import com.ghalbitnet.meshx2.vpn.VpnLogManager
import java.net.InetAddress
import org.json.JSONObject

object DeviceNameResolver {

    private const val PREFS_NAME = "ghalbit_device_name_cache"
    private const val KEY_CACHE = "device_names"

    fun resolve(
        context: Context,
        ipAddress: String,
        fallbackName: String? = null
    ): String? {
        if (fallbackName?.isNotBlank() == true) {
            cache(context, ipAddress, fallbackName)
            VpnLogManager.info("CLIENT_NAME_RESOLVED", "ip=$ipAddress name=$fallbackName source=fallback")
            return fallbackName
        }
        val cached = cached(context, ipAddress)
        if (!cached.isNullOrBlank()) {
            return cached
        }
        val reverseDnsName =
            runCatching {
                val canonical = InetAddress.getByName(ipAddress).canonicalHostName.orEmpty()
                canonical.takeIf { it.isNotBlank() && it != ipAddress }
            }.getOrNull()
        if (!reverseDnsName.isNullOrBlank()) {
            cache(context, ipAddress, reverseDnsName)
            VpnLogManager.info("CLIENT_NAME_RESOLVED", "ip=$ipAddress name=$reverseDnsName source=reverse_dns")
            return reverseDnsName
        }
        return null
    }

    private fun cached(
        context: Context,
        ipAddress: String
    ): String? {
        val raw =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_CACHE, "{}")
                .orEmpty()
        val json = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        return json.optString(ipAddress).takeIf { it.isNotBlank() }
    }

    private fun cache(
        context: Context,
        ipAddress: String,
        deviceName: String
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CACHE, "{}").orEmpty()
        val json = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        json.put(ipAddress, deviceName)
        prefs.edit().putString(KEY_CACHE, json.toString()).apply()
    }
}
