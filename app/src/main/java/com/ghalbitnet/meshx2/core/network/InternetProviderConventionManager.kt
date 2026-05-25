package com.ghalbitnet.meshx2.core.network

object InternetProviderConventionManager {

    const val STANDARD_PASSWORD = "12345678"
    private const val SSID_PREFIX = "GHALBIT-MESH-"

    fun recommendedSsid(globalId: String): String {
        val clean = globalId.removePrefix("GX-").takeLast(6).ifBlank { "LOCAL" }
        return SSID_PREFIX + clean
    }

    fun isAligned(
        hotspotSsid: String,
        hotspotPassword: String,
        globalId: String
    ): Boolean {
        return hotspotSsid.trim().equals(recommendedSsid(globalId), ignoreCase = true) &&
            hotspotPassword.trim() == STANDARD_PASSWORD
    }
}
