package com.ghalbitnet.meshx2.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.ghalbitnet.meshx2.core.node.NodeStatusManager
import java.net.Inet4Address
import java.net.NetworkInterface

object ConnectivityScopeDetector {

    enum class Scope(
        val label: String
    ) {
        INTERNET_AND_LOCAL("Internet + Lokal"),
        LOCAL_ONLY("Hanya Lokal"),
        INTERNET_ONLY("Internet tersedia"),
        OFFLINE("Offline")
    }

    data class Status(
        val scope: Scope,
        val hasInternet: Boolean,
        val hasLocal: Boolean,
        val detail: String
    )

    fun detect(context: Context): Status {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        val activeNetwork =
            connectivityManager?.activeNetwork
        val capabilities =
            activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }

        val hasInternet =
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        val hasPrivateIpv4 =
            hasPrivateLocalIpv4()
        val hasLocalTransport =
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        val hasNearbyLocalNode =
            NodeStatusManager.getOnlineNodes().any { node ->
                when (TransportPreference.modeForAddress(node.ipAddress)) {
                    TransportPreference.Mode.LAN_HOTSPOT,
                    TransportPreference.Mode.NEARBY -> true
                    else -> false
                }
            }

        val hasLocal =
            hasPrivateIpv4 || hasLocalTransport || hasNearbyLocalNode

        return when {
            hasInternet && hasLocal ->
                Status(
                    scope = Scope.INTERNET_AND_LOCAL,
                    hasInternet = true,
                    hasLocal = true,
                    detail = "Internet aktif dan jalur lokal tersedia."
                )

            hasLocal ->
                Status(
                    scope = Scope.LOCAL_ONLY,
                    hasInternet = false,
                    hasLocal = true,
                    detail = "Jaringan lokal tersedia walau internet belum terdeteksi."
                )

            hasInternet ->
                Status(
                    scope = Scope.INTERNET_ONLY,
                    hasInternet = true,
                    hasLocal = false,
                    detail = "Internet tersedia, tetapi jalur lokal belum terlihat."
                )

            else ->
                Status(
                    scope = Scope.OFFLINE,
                    hasInternet = false,
                    hasLocal = false,
                    detail = "Belum ada internet atau jalur lokal yang siap."
                )
        }
    }

    private fun hasPrivateLocalIpv4(): Boolean {
        return try {
            val interfaces =
                NetworkInterface.getNetworkInterfaces()

            while (interfaces.hasMoreElements()) {
                val networkInterface =
                    interfaces.nextElement()

                if (!networkInterface.isUp || networkInterface.isLoopback) {
                    continue
                }

                val addresses =
                    networkInterface.inetAddresses

                while (addresses.hasMoreElements()) {
                    val address =
                        addresses.nextElement()

                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val host =
                            address.hostAddress ?: continue

                        if (TransportPreference.modeForAddress(host) == TransportPreference.Mode.LAN_HOTSPOT) {
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
}
