package com.ghalbitnet.meshx2.core.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.ghalbitnet.meshx2.activityfeed.ActivityFeedManager
import com.ghalbitnet.meshx2.activityfeed.ActivityFeedType
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
        ActivityFeedManager.bind(context)
        if (!started.compareAndSet(false, true)) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val initialIp = resolveCurrentIpv4(preferRoutable = true)
        currentIp = initialIp
        currentSubnet = subnetOf(initialIp)
        Log.d(TAG, "start initialIp=$currentIp initialSubnet=$currentSubnet")
        publishNetworkEvent(
            title = "Monitor jaringan aktif",
            message = "IP awal $currentIp, subnet $currentSubnet, tipe $networkType",
            metadata = "{\"ip\":\"$currentIp\",\"subnet\":\"$currentSubnet\",\"networkType\":\"$networkType\"}"
        )
        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateNetworkType(cm, network)
                Log.d(TAG, "available network=$network type=$networkType")
                publishNetworkEvent(
                    title = "Jaringan tersedia",
                    message = "Koneksi $networkType tersedia, memeriksa perubahan IP/subnet.",
                    metadata = "{\"reason\":\"available\",\"networkType\":\"$networkType\"}"
                )
                evaluateNetworkChange(listener, reason = "available")
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "lost network=$network previousType=$networkType")
                publishNetworkEvent(
                    title = "Jaringan terputus",
                    message = "Koneksi $networkType hilang, recovery mesh akan dievaluasi.",
                    metadata = "{\"reason\":\"lost\",\"networkType\":\"$networkType\"}"
                )
                evaluateNetworkChange(listener, reason = "lost")
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                updateNetworkType(cm, network)
                Log.d(TAG, "capabilitiesChanged network=$network type=$networkType")
                evaluateNetworkChange(listener, reason = "capabilitiesChanged")
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
            publishNetworkEvent(
                type = ActivityFeedType.SYNC_FAILED,
                title = "Monitor jaringan gagal",
                message = e.message ?: "Network callback gagal didaftarkan.",
                metadata = "{\"reason\":\"startFailed\"}"
            )
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
        publishNetworkEvent(
            title = "Monitor jaringan berhenti",
            message = "Network handoff monitor dihentikan.",
            metadata = "{\"reason\":\"stop\"}"
        )
    }

    fun markRediscovering(value: Boolean) {
        if (rediscovering == value) return
        rediscovering = value
        publishNetworkEvent(
            title = if (value) "Rediscovery dimulai" else "Rediscovery selesai",
            message = if (value) "Mesh sedang mencari ulang node setelah perubahan jaringan." else "Proses pencarian ulang node selesai.",
            metadata = "{\"rediscovering\":$value}"
        )
    }

    fun updateTcpListenerRunning(value: Boolean) {
        if (tcpListenerRunning == value) return
        tcpListenerRunning = value
        publishNetworkEvent(
            title = if (value) "TCP listener aktif" else "TCP listener tidak aktif",
            message = if (value) "Server socket mesh siap menerima koneksi." else "Server socket mesh belum siap menerima koneksi.",
            metadata = "{\"tcpListenerRunning\":$value}"
        )
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

    private fun evaluateNetworkChange(listener: Listener, reason: String) {
        val oldIp = currentIp
        val oldSubnet = currentSubnet
        val newIp = resolveCurrentIpv4(preferRoutable = true)
        val newSubnet = subnetOf(newIp)
        Log.d(TAG, "reason=$reason oldIp=$oldIp newIp=$newIp")
        Log.d(TAG, "reason=$reason oldSubnet=$oldSubnet newSubnet=$newSubnet")

        if (newIp == "-") {
            Log.w(TAG, "ipUnavailable reason=$reason keepOldIp=$oldIp networkType=$networkType")
            publishNetworkEvent(
                type = ActivityFeedType.SYNC_FAILED,
                title = "IP belum tersedia",
                message = "Perubahan jaringan terdeteksi, tetapi IP baru belum tersedia. Alamat lama tetap dipakai sementara.",
                metadata = "{\"reason\":\"$reason\",\"oldIp\":\"$oldIp\",\"networkType\":\"$networkType\"}"
            )
            if (oldIp != "-") {
                listener.onSubnetChanged(oldIp, oldIp, oldSubnet, oldSubnet)
                Log.d(TAG, "recoveryTriggeredWithoutNewIp reason=$reason")
            }
            return
        }

        val ipChanged = oldIp != "-" && oldIp != newIp
        val subnetChanged = oldSubnet != "-" && newSubnet != "-" && oldSubnet != newSubnet
        val firstIpResolved = oldIp == "-" && newIp != "-"
        Log.d(TAG, "subnetChanged=$subnetChanged ipChanged=$ipChanged firstIpResolved=$firstIpResolved")
        currentIp = newIp
        currentSubnet = newSubnet
        if (subnetChanged || ipChanged || firstIpResolved) {
            publishNetworkEvent(
                type = ActivityFeedType.SYNC_SUCCESS,
                title = "Perubahan jaringan terdeteksi",
                message = "IP $oldIp → $newIp, subnet $oldSubnet → $newSubnet. Rediscovery mesh dijalankan.",
                metadata = "{\"reason\":\"$reason\",\"oldIp\":\"$oldIp\",\"newIp\":\"$newIp\",\"oldSubnet\":\"$oldSubnet\",\"newSubnet\":\"$newSubnet\",\"networkType\":\"$networkType\"}"
            )
            listener.onSubnetChanged(oldIp, newIp, oldSubnet, newSubnet)
        }
    }

    private fun updateNetworkType(cm: ConnectivityManager, network: Network) {
        val caps = cm.getNetworkCapabilities(network)
        networkType = when {
            caps == null -> "UNKNOWN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "OTHER"
        }
    }

    private fun publishNetworkEvent(
        type: ActivityFeedType = ActivityFeedType.RUNTIME_EVENT,
        title: String,
        message: String,
        metadata: String? = null
    ) {
        ActivityFeedManager.publish(
            type = type,
            title = title,
            message = message,
            source = "NetworkHandoffMonitor",
            metadata = metadata
        )
    }

    private fun resolveCurrentIpv4(preferRoutable: Boolean): String {
        return try {
            val candidates = mutableListOf<Pair<String, String>>()
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback || iface.isVirtual) continue
                val name = iface.name.lowercase()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress && !address.isAnyLocalAddress) {
                        val ip = address.hostAddress ?: continue
                        candidates += name to ip
                    }
                }
            }
            if (candidates.isEmpty()) return "-"
            val preferred =
                if (preferRoutable) {
                    candidates.firstOrNull { (name, _) ->
                        name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan")
                    } ?: candidates.firstOrNull { (name, _) ->
                        name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("pdp") || name.startsWith("usb") || name.startsWith("eth")
                    } ?: candidates.first()
                } else {
                    candidates.first()
                }
            Log.d(TAG, "ipv4Candidates=${candidates.joinToString { "${it.first}:${it.second}" }} selected=${preferred.first}:${preferred.second}")
            preferred.second
        } catch (e: Exception) {
            Log.e(TAG, "resolveCurrentIpv4 failed", e)
            "-"
        }
    }

    private fun subnetOf(ip: String): String {
        val parts = ip.split(".")
        return if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}.0/24" else "-"
    }
}
