package com.ghalbitnet.meshx2.core.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicBoolean

object NetworkHandoffMonitor {

    data class Snapshot(
        val currentIp: String,
        val currentSubnet: String,
        val networkType: String,
        val tcpListenerRunning: Boolean,
        val lastRouteChange: Long,
        val rediscovering: Boolean
    )

    interface Listener {
        fun onSubnetChanged(oldIp: String, newIp: String, oldSubnet: String, newSubnet: String)
    }

    private val started = AtomicBoolean(false)
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var listener: Listener? = null
    @Volatile
    private var currentIp: String = ""
    @Volatile
    private var currentSubnet: String = ""
    @Volatile
    private var networkType: String = "UNKNOWN"
    @Volatile
    private var lastRouteChange: Long = 0L
    @Volatile
    private var rediscovering: Boolean = false
    @Volatile
    private var tcpListenerRunning: Boolean = false

    fun start(context: Context, listener: Listener) {
        if (!started.compareAndSet(false, true)) {
            this.listener = listener
            return
        }
        this.listener = listener
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: run {
                    started.set(false)
                    return
                }
        refreshNetworkSnapshot(connectivityManager)
        val networkCallback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d("GHALBIT-NETWORK-HANDOFF", "available")
                    handleNetworkStateChanged(connectivityManager, "available")
                }

                override fun onLost(network: Network) {
                    Log.d("GHALBIT-NETWORK-HANDOFF", "lost")
                    handleNetworkStateChanged(connectivityManager, "lost")
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    Log.d("GHALBIT-NETWORK-HANDOFF", "capabilitiesChanged")
                    networkType = networkTypeLabel(caps)
                    handleNetworkStateChanged(connectivityManager, "capabilitiesChanged")
                }
            }
        callback = networkCallback
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    fun stop(context: Context) {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val networkCallback = callback
        if (connectivityManager != null && networkCallback != null) {
            runCatching {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            }
        }
        callback = null
        listener = null
        started.set(false)
        rediscovering = false
    }

    fun markRediscovering(active: Boolean) {
        rediscovering = active
    }

    fun updateTcpListenerRunning(running: Boolean) {
        tcpListenerRunning = running
    }

    fun snapshot(): Snapshot {
        return Snapshot(
            currentIp = currentIp,
            currentSubnet = currentSubnet,
            networkType = networkType,
            tcpListenerRunning = tcpListenerRunning,
            lastRouteChange = lastRouteChange,
            rediscovering = rediscovering
        )
    }

    private fun handleNetworkStateChanged(connectivityManager: ConnectivityManager, event: String) {
        val oldIp = currentIp
        val oldSubnet = currentSubnet
        refreshNetworkSnapshot(connectivityManager)
        Log.d("GHALBIT-NETWORK-HANDOFF", "oldIp=$oldIp")
        Log.d("GHALBIT-NETWORK-HANDOFF", "newIp=$currentIp")
        val subnetChanged = oldSubnet.isNotBlank() && currentSubnet.isNotBlank() && oldSubnet != currentSubnet
        Log.d("GHALBIT-NETWORK-HANDOFF", "subnetChanged=$subnetChanged event=$event")
        if (subnetChanged) {
            lastRouteChange = System.currentTimeMillis()
            listener?.onSubnetChanged(oldIp, currentIp, oldSubnet, currentSubnet)
        }
    }

    private fun refreshNetworkSnapshot(connectivityManager: ConnectivityManager) {
        currentIp = findLocalIpv4Address().orEmpty()
        currentSubnet = subnetFromIp(currentIp)
        val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        networkType = networkTypeLabel(caps)
    }

    private fun networkTypeLabel(caps: NetworkCapabilities?): String {
        if (caps == null) return "NONE"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "OTHER"
        }
    }

    private fun subnetFromIp(ip: String): String {
        val parts = ip.split('.')
        if (parts.size != 4) return ""
        return "${parts[0]}.${parts[1]}.${parts[2]}.0/24"
    }

    private fun findLocalIpv4Address(): String? {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                val name = networkInterface.name.lowercase()
                if (
                    name.startsWith("rmnet") ||
                    name.startsWith("ccmni") ||
                    name.startsWith("pdp")
                ) {
                    continue
                }
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress
                    }
                }
            }
            null
        }.getOrNull()
    }
}
