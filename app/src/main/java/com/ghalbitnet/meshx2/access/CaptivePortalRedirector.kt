package com.ghalbitnet.meshx2.access

import android.content.Context
import com.ghalbitnet.meshx2.vpn.VpnLogManager

object CaptivePortalRedirector {

    fun noteUnauthorizedClient(context: Context, ipAddress: String) {
        val url = CaptivePortalPolicy.gatewayUrl(context)
        VpnLogManager.warn(
            "CAPTIVE_PORTAL_REDIRECT_ATTEMPT",
            "client=$ipAddress target=$url"
        )
        VpnLogManager.warn(
            "REDIRECT_REQUIRES_GATEWAY_CONTROL",
            "Android standar belum bisa redirect semua trafik hotspot. Minta klien buka $url secara manual."
        )
    }
}
