package com.ghalbitnet.meshx2.access

import android.content.Context
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.ghalbitnet.meshx2.vpn.VpnLogManager

object LocalSubnetScanner {

    private const val HOST_SCAN_TIMEOUT_MS = 220
    private const val MAX_WORKERS = 16

    suspend fun scan(
        context: Context,
        limitPerSubnet: Int = 254
    ): List<String> =
        coroutineScope {
            val targets = candidateIps(context, limitPerSubnet)
            VpnLogManager.info("SUBNET_SCAN_STARTED", "targets=${targets.size}")
            val semaphore = Semaphore(MAX_WORKERS)
            targets.map { ip ->
                async(Dispatchers.IO) {
                    val reachable =
                        semaphore.withPermit {
                            runCatching { InetAddress.getByName(ip).isReachable(HOST_SCAN_TIMEOUT_MS) }
                                .getOrDefault(false)
                        }
                    if (reachable) {
                        VpnLogManager.info("SUBNET_SCAN_HOST_REACHABLE", ip)
                    }
                    if (reachable) ip else null
                }
            }.awaitAll().filterNotNull().also {
                VpnLogManager.info("SUBNET_SCAN_FINISHED", "reachable=${it.size} targets=${targets.size}")
            }
        }

    private fun candidateIps(
        context: Context,
        limitPerSubnet: Int
    ): List<String> {
        val prefixes = mutableSetOf<String>()
        val gatewaySnapshot = HotspotGatewayDetector.detect(context)
        prefixes += gatewaySnapshot.subnetPrefixes
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                val name = networkInterface.name.lowercase()
                if (
                    !name.contains("wlan") &&
                        !name.contains("ap") &&
                        !name.contains("swlan") &&
                        !name.contains("softap")
                ) {
                    continue
                }
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val host = address.hostAddress ?: continue
                        val prefix = host.substringBeforeLast('.', "")
                        if (prefix.count { it == '.' } == 2) {
                            prefixes += prefix
                        }
                    }
                }
            }
        }
        if (prefixes.isEmpty()) {
            prefixes += setOf("192.168.43", "192.168.1", "192.168.100")
        }
        return prefixes.flatMap { prefix ->
            (2..limitPerSubnet.coerceAtMost(254)).map { host -> "$prefix.$host" }
        }.distinct()
    }
}
