package com.ghalbitnet.meshx2.access

import com.ghalbitnet.meshx2.vpn.VpnLogManager

object SilentClientDetector {

    data class SilentClient(
        val ipAddress: String,
        val macAddress: String?,
        val deviceName: String?,
        val reason: String
    )

    fun detect(
        arpEntries: List<ArpTableReader.ArpEntry>,
        reachableIps: List<String>
    ): List<SilentClient> {
        val arpByIp = arpEntries.associateBy { it.ipAddress }
        val visibleIps = (arpByIp.keys + reachableIps).filter { it.isNotBlank() }.distinct()
        return visibleIps.mapNotNull { ip ->
            if (PeerAuthRegistry.getByIp(ip) != null) return@mapNotNull null
            val existing = UnauthorizedClientRegistry.snapshot(ip)
            if (existing?.status == NetworkAccessPolicy.AuthStatus.AUTHORIZED) return@mapNotNull null
            val arp = arpByIp[ip]
            val reason = "Tidak ada HELLO_AUTH"
            VpnLogManager.warn(
                "HOTSPOT_SILENT_CLIENT_DETECTED",
                "ip=$ip mac=${arp?.macAddress ?: "-"} device=${arp?.device ?: "-"}"
            )
            SilentClient(
                ipAddress = ip,
                macAddress = arp?.macAddress,
                deviceName = arp?.device,
                reason = reason
            )
        }
    }
}
