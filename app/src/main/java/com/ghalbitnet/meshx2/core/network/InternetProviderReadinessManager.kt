package com.ghalbitnet.meshx2.core.network

import android.content.Context
import android.net.wifi.WifiManager
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.economy.InternetBridgeUsageMonitor
import com.ghalbitnet.meshx2.economy.InternetGatewayLoadManager
import com.ghalbitnet.meshx2.settings.OnboardingManager
import java.net.Inet4Address
import java.net.NetworkInterface

object InternetProviderReadinessManager {

    enum class Status {
        OFFLINE,
        PERSONAL_ONLY,
        PREPARING,
        READY,
        ACTIVE
    }

    data class Snapshot(
        val hasInternet: Boolean,
        val hotspotActive: Boolean,
        val consentGiven: Boolean,
        val credentialsReady: Boolean,
        val conventionAligned: Boolean,
        val providerReady: Boolean,
        val providerActive: Boolean,
        val shareName: String,
        val status: Status
    ) {
        fun label(context: Context): String {
            return when (status) {
                Status.OFFLINE -> context.getString(R.string.provider_status_offline)
                Status.PERSONAL_ONLY -> context.getString(R.string.provider_status_personal)
                Status.PREPARING -> context.getString(R.string.provider_status_preparing)
                Status.READY -> context.getString(R.string.provider_status_ready)
                Status.ACTIVE -> context.getString(R.string.provider_status_active)
            }
        }

        fun detail(context: Context): String {
            return when (status) {
                Status.OFFLINE -> context.getString(R.string.provider_status_offline_detail)
                Status.PERSONAL_ONLY -> context.getString(R.string.provider_status_personal_detail)
                Status.PREPARING -> context.getString(R.string.provider_status_preparing_detail)
                Status.READY -> context.getString(R.string.provider_status_ready_detail, shareName)
                Status.ACTIVE -> context.getString(R.string.provider_status_active_detail, shareName)
            }
        }
    }

    fun snapshot(context: Context): Snapshot {
        val connectivity = ConnectivityStatusDetector.snapshot(context, emptyList())
        val profile = InternetProviderAccessManager.snapshot(context)
        val onboarding = OnboardingManager.snapshot(context)
        val globalId =
            GlobalMeshIdentityManager.buildGlobalId(
                com.ghalbitnet.meshx2.security.KeyStoreManager(context).publicKeyBase64
            )
        val hotspotActive = isHotspotActive(context)
        val conventionAligned = profile.isConventionAligned(globalId)
        val providerReady =
            connectivity.hasInternet &&
                hotspotActive &&
                onboarding.completed &&
                onboarding.contributionApproved &&
                onboarding.providerEnabled &&
                profile.consentGiven &&
                profile.credentialsReady &&
                conventionAligned
        val providerActive =
            providerReady &&
                (
                    InternetGatewayLoadManager.activeLoad(context, "local") > 0 ||
                        InternetBridgeUsageMonitor.activeSessionSnapshot(context)?.gatewayNodeId == "local"
                    )

        val status =
            when {
                !connectivity.hasInternet -> Status.OFFLINE
                providerActive -> Status.ACTIVE
                providerReady -> Status.READY
                hotspotActive || profile.consentGiven || profile.credentialsReady -> Status.PREPARING
                else -> Status.PERSONAL_ONLY
            }

        return Snapshot(
            hasInternet = connectivity.hasInternet,
            hotspotActive = hotspotActive,
            consentGiven = profile.consentGiven,
            credentialsReady = profile.credentialsReady,
            conventionAligned = conventionAligned,
            providerReady = providerReady,
            providerActive = providerActive,
            shareName = profile.hotspotSsid.ifBlank { context.getString(R.string.provider_default_name) },
            status = status
        )
    }

    fun shouldAdvertiseGateway(context: Context): Boolean {
        return snapshot(context).providerReady
    }

    private fun isHotspotActive(context: Context): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val reflected =
            runCatching {
                val method = wifiManager?.javaClass?.getDeclaredMethod("isWifiApEnabled")
                method?.isAccessible = true
                method?.invoke(wifiManager) as? Boolean
            }.getOrNull()

        if (reflected == true) {
            return true
        }

        return hasLikelyTetherInterface()
    }

    private fun hasLikelyTetherInterface(): Boolean {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val name = networkInterface.name.orEmpty().lowercase()
                if (
                    !networkInterface.isUp ||
                    networkInterface.isLoopback ||
                    !looksLikeTetherInterface(name)
                ) {
                    continue
                }

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val hostAddress = address.hostAddress.orEmpty()
                        if (
                            hostAddress.startsWith("192.168.") ||
                            hostAddress.startsWith("10.") ||
                            hostAddress.matches(Regex("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*"))
                        ) {
                            return true
                        }
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun looksLikeTetherInterface(name: String): Boolean {
        return name.contains("ap") ||
            name.contains("softap") ||
            name.contains("swlan") ||
            name.contains("wlan1") ||
            name.contains("rndis") ||
            name.contains("usb")
    }
}
