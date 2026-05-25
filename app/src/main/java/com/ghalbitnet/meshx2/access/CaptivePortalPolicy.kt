package com.ghalbitnet.meshx2.access

import android.content.Context

object CaptivePortalPolicy {

    fun shouldRun(context: Context): Boolean {
        val provider = com.ghalbitnet.meshx2.core.network.InternetProviderReadinessManager.snapshot(context)
        return provider.hotspotActive
    }

    fun gatewayUrl(context: Context): String {
        val host = CaptivePortalServer.gatewayIp(context)
        return "http://$host:${CaptivePortalServer.PORT}"
    }
}
