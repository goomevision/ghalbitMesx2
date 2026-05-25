package com.ghalbitnet.meshx2.access

import android.content.Context
import android.net.wifi.WifiManager
import com.ghalbitnet.meshx2.vpn.VpnLogManager
import java.net.Inet4Address
import java.net.NetworkInterface

object HotspotGatewayDetector {

    data class Snapshot(
        val gatewayIp: String?,
        val subnetPrefixes: List<String>
    )

    fun detect(context: Context): Snapshot {
        val prefixes = linkedSetOf<String>()
        var gatewayIp: String? = null

        runCatching {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val dhcpInfo = wifiManager?.dhcpInfo
            if (dhcpInfo != null) {
                val gateway = intToIp(dhcpInfo.gateway)
                val deviceIp = intToIp(dhcpInfo.ipAddress)
                gatewayIp = gateway.takeIf { it.isNotBlank() && it != "0.0.0.0" }
                deviceIp.substringBeforeLast('.', "").takeIf { it.count { ch -> ch == '.' } == 2 }?.let(prefixes::add)
                gatewayIp?.substringBeforeLast('.', "")?.takeIf { it.count { ch -> ch == '.' } == 2 }?.let(prefixes::add)
                if (!gatewayIp.isNullOrBlank()) {
                    VpnLogManager.info("HOTSPOT_GATEWAY_DETECTED", "gateway=$gatewayIp source=dhcp")
                }
            }
        }

        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                val name = networkInterface.name.lowercase()
                if (
                    !name.contains("wlan") &&
                        !name.contains("ap") &&
                        !name.contains("softap") &&
                        !name.contains("rndis") &&
                        !name.contains("tether")
                ) {
                    continue
                }
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val host = address.hostAddress ?: continue
                        host.substringBeforeLast('.', "")
                            .takeIf { it.count { ch -> ch == '.' } == 2 }
                            ?.let(prefixes::add)
                        if (gatewayIp == null) {
                            gatewayIp = host
                            VpnLogManager.info("HOTSPOT_GATEWAY_DETECTED", "gateway=$gatewayIp source=interface:$name")
                        }
                    }
                }
            }
        }

        if (prefixes.isEmpty()) {
            VpnLogManager.warn("HOTSPOT_SUBNET_UNKNOWN", "Subnet hotspot belum bisa dipastikan.")
        } else {
            VpnLogManager.info("HOTSPOT_SUBNET_DETECTED", prefixes.joinToString(","))
        }
        return Snapshot(gatewayIp = gatewayIp, subnetPrefixes = prefixes.toList())
    }

    private fun intToIp(value: Int): String {
        return listOf(
            value and 0xff,
            value shr 8 and 0xff,
            value shr 16 and 0xff,
            value shr 24 and 0xff
        ).joinToString(".")
    }
}
