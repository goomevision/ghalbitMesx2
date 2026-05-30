package com.ghalbitnet.meshx2.core.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicBoolean

object NetworkHandoffMonitor {
    private const val TAG = "GHALBIT-NETWORK-HANDOFF"

    interface Listener {
        fun onSubnetChanged(oldIp: String, newIp: String, oldSubnet: String, newSubnet: String)
    }

    data class Snapshot(
        val currentIp: String,
        val currentSubnet: String,
        val networkType: String,
        val tcpListenerRunning: Boolean,
        val rediscovering: Boolean
    )

    @Volatile private var callbackRegistered = false
    @Volatile private var callback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var currentIp: String = "-"
    @Volatile private var currentSubnet: String = "-"
    @Volatile private var networkType: String = "UNKNOWN"
    @Volatile private var tcpListenerRunning: Boolean = false
    @Volatile private var rediscovering: Boolean = false
    private val started = AtomicBoolean(false)

    fun start(context: Context, listener: Listener) {
        if (!started.compareAndSet(false, true)) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val initialIp = resolveCurrentIpv4()
        currentIp = initialIp
        currentSubnet = subnetOf(initialIp)
        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateNetworkType(cm, network)
                Log.d(TAG, "available network=$network")
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "lost network=$network")
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                updateNetworkType(cm, network)
                Log.d(TAG, "capabilitiesChanged network=$network")
                val oldIp = currentIp
                val oldSubnet = currentSubnet
                val newIp = resolveCurrentIpv4()
                val newSubnet = subnetOf(newIp)
                Log.d(TAG, "oldIp=$oldIp")
                Log.d(TAG, "newIp=$newIp")
                val subnetChanged = oldSubnet != "-" && newSubnet != "-" && oldSubnet != newSubnet
                Log.d(TAG, "subnetChanged=$subnetChanged oldSubnet=$oldSubnet newSubnet=$newSubnet")
                currentIp = newIp
                currentSubnet = newSubnet
                if (subnetChanged) {
                    listener.onSubnetChanged(oldIp, newIp, oldSubnet, newSubnet)
                }
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(callback!!)
            } else {
                @Suppress("DEPRECATION")
                cm.registerNetworkCallback(android.net.NetworkRequest.Builder().build(), callback!!)
            }
            callbackRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
            started.set(false)
        }
    }

    fun stop(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        if (callbackRegistered && callback != null) {
            try {
                cm.unregisterNetworkCallback(callback!!)
            } catch (_: Exception) {
            }
        }
        callbackRegistered = false
        callback = null
        started.set(false)
    }

    fun markRediscovering(value: Boolean) {
        rediscovering = value
    }

    fun updateTcpListenerRunning(value: Boolean) {
        tcpListenerRunning = value
    }

    fun snapshot(): Snapshot {
        return Snapshot(
            currentIp = currentIp,
            currentSubnet = currentSubnet,
            networkType = networkType,
            tcpListenerRunning = tcpListenerRunning,
            rediscovering = rediscovering
        )
    }

    private fun updateNetworkType(cm: ConnectivityManager, network: Network) {
        val caps = cm.getNetworkCapabilities(network)
        networkType = when {
            caps == null -> "UNKNOWN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "OTHER"
        }
    }

    private fun resolveCurrentIpv4(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val name = iface.name.lowercase()
                if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("pdp")) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress ?: "-"
                    }
                }
            }
            "-"
        } catch (_: Exception) {
            "-"
        }
    }

    private fun subnetOf(ip: String): String {
        val parts = ip.split(".")
        return if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}.0/24" else "-"
    }
}
