package com.ghalbitnet.meshx2.access

import android.content.Context
import com.ghalbitnet.meshx2.vpn.VpnLogManager

object HotspotNetworkScanner {

    suspend fun scan(context: Context) {
        VpnLogManager.info("HOTSPOT_CLIENT_SCAN_STARTED", "Memulai scan client hotspot.")
        val arpEntries = ArpTableReader.read()
        arpEntries.forEach { entry ->
            VpnLogManager.info(
                "HOTSPOT_ARP_CLIENT_FOUND",
                "ip=${entry.ipAddress} mac=${entry.macAddress ?: "-"} device=${entry.device ?: "-"}"
            )
        }
        val reachableIps =
            if (arpEntries.isEmpty()) {
                VpnLogManager.warn(
                    "ARP_EMPTY_FALLBACK_TO_SUBNET_SCAN",
                    "Tabel ARP kosong, lanjut scan subnet hotspot."
                )
                LocalSubnetScanner.scan(context.applicationContext)
            } else {
                emptyList()
            }
        val silentClients = SilentClientDetector.detect(arpEntries, reachableIps)
        silentClients.forEach { client ->
            val resolvedName =
                DeviceNameResolver.resolve(
                    context = context.applicationContext,
                    ipAddress = client.ipAddress,
                    fallbackName = client.deviceName
                )
            UnauthorizedClientRegistry.markSilentClient(
                ipAddress = client.ipAddress,
                macAddress = client.macAddress,
                deviceName = resolvedName ?: client.deviceName,
                detail = client.reason
            )
            UnauthorizedClientNotifier.showIfNew(
                context.applicationContext,
                ipAddress = client.ipAddress,
                macAddress = client.macAddress
            )
            VpnLogManager.warn(
                "UNKNOWN_NO_HELLO_AUTH_CLIENT_REGISTERED",
                "ip=${client.ipAddress} mac=${client.macAddress ?: "-"}"
            )
        }
        if (silentClients.isNotEmpty()) {
            CommunitySessionRepository.syncFromCurrentState(context.applicationContext)
        }
        VpnLogManager.info(
            "HOTSPOT_CLIENT_SCAN_FINISHED",
            "arp=${arpEntries.size} silent=${silentClients.size} scannedReachable=${reachableIps.size}"
        )
    }
}
