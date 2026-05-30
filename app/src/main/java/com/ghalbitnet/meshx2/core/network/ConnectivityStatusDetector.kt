package com.ghalbitnet.meshx2.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.model.MeshNode
import java.net.Inet4Address
import java.net.NetworkInterface

object ConnectivityStatusDetector {

    enum class Scope {
        INTERNET_AND_LOCAL,
        LOCAL_ONLY,
        INTERNET_ONLY,
        OFFLINE
    }

    data class Snapshot(
        val scope: Scope,
        val hasInternet: Boolean,
        val hasLocal: Boolean,
        val detail: TransportPreference.Mode
    ) {
        fun title(context: Context): String {
            return when (scope) {
                Scope.INTERNET_AND_LOCAL -> context.getString(R.string.connection_scope_internet_local)
                Scope.LOCAL_ONLY -> context.getString(R.string.connection_scope_local_only)
                Scope.INTERNET_ONLY -> context.getString(R.string.connection_scope_internet_only)
                Scope.OFFLINE -> context.getString(R.string.connection_scope_offline)
            }
        }

        fun description(context: Context): String {
            return when (scope) {
                Scope.INTERNET_AND_LOCAL -> context.getString(R.string.connection_scope_desc_internet_local)
                Scope.LOCAL_ONLY -> context.getString(R.string.connection_scope_desc_local_only)
                Scope.INTERNET_ONLY -> context.getString(R.string.connection_scope_desc_internet_only)
                Scope.OFFLINE -> context.getString(R.string.connection_scope_desc_offline)
            }
        }
    }

    fun roleLabel(
        context: Context,
        gateway: Boolean,
        relay: Boolean
    ): String {
        return when {
            gateway && relay -> context.getString(R.string.node_role_gateway_and_relay)
            gateway -> context.getString(R.string.node_role_gateway)
            relay -> context.getString(R.string.node_role_relay)
            else -> context.getString(R.string.node_role_unknown)
        }
    }

    fun snapshot(
        context: Context,
        nodes: List<MeshNode>
    ): Snapshot {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        val activeNetwork = connectivityManager?.activeNetwork
        val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)

        val hasValidatedInternet =
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

        val hasInternetCapability =
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        val hasInternet = hasValidatedInternet || hasInternetCapability

        val localNodeMode =
            nodes
                .filter { it.online }
                .map { TransportPreference.modeForAddress(it.ipAddress) }
                .firstOrNull { it != TransportPreference.Mode.UNKNOWN }

        val hasPrivateAddress = hasPrivateIpv4Address()
        val hasLocalTransport =
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true

        val hasLocal =
            localNodeMode == TransportPreference.Mode.LAN_HOTSPOT ||
                localNodeMode == TransportPreference.Mode.NEARBY ||
                (hasLocalTransport && hasPrivateAddress)

        val scope =
            when {
                hasInternet && hasLocal -> Scope.INTERNET_AND_LOCAL
                hasLocal -> Scope.LOCAL_ONLY
                hasInternet -> Scope.INTERNET_ONLY
                else -> Scope.OFFLINE
            }

        val detail =
            localNodeMode
                ?: if (hasLocalTransport && hasPrivateAddress) {
                    TransportPreference.Mode.LAN_HOTSPOT
                } else {
                    TransportPreference.Mode.UNKNOWN
                }

        return Snapshot(
            scope = scope,
            hasInternet = hasInternet,
            hasLocal = hasLocal,
            detail = detail
        )
    }

    fun localGatewayActive(context: Context): Boolean {
        return snapshot(context, emptyList()).hasInternet
    }

    private fun hasPrivateIpv4Address(): Boolean {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()

            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()

                if (networkInterface.isLoopback || !networkInterface.isUp) {
                    continue
                }

                val addresses = networkInterface.inetAddresses

                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()

                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val hostAddress = address.hostAddress ?: continue

                        if (
                            hostAddress.startsWith("10.") ||
                            hostAddress.startsWith("192.168.") ||
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
}
