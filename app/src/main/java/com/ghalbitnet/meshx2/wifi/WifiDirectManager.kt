package com.ghalbitnet.meshx2.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import com.ghalbitnet.meshx2.discovery.DiscoveryManager
import com.ghalbitnet.meshx2.model.MeshNode

class WifiDirectManager(private val context: Context) {
    private val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager?
    private var channel: WifiP2pManager.Channel? = null
    private var receiverRegistered = false

    private val peersListener = WifiP2pManager.PeerListListener { peers ->
        val nodes = peers.deviceList.map { d ->
            MeshNode(
                name = d.deviceName, ipAddress = d.deviceAddress,
                signal = if (d.status == WifiP2pDevice.CONNECTED) 100 else 50,
                online = d.status == WifiP2pDevice.AVAILABLE || d.status == WifiP2pDevice.CONNECTED
            )
        }
        DiscoveryManager.addNodes(nodes)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    val activeChannel = channel ?: return
                    try {
                        manager?.requestPeers(activeChannel, peersListener)
                    } catch (e: SecurityException) {
                        Log.e("GHALBIT", "WiFi Direct peer request blocked by permission", e)
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {}
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            receiverRegistered = true
        } catch (e: Exception) {
            Log.e("GHALBIT", "WiFi Direct receiver register failed", e)
        }

        channel = manager?.initialize(context, context.mainLooper, null)

        val activeChannel = channel
        if (manager != null && activeChannel != null) {
            try {
                manager.discoverPeers(activeChannel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { Log.d("GHALBIT", "WiFi Direct discovery started") }
                    override fun onFailure(reason: Int) { Log.e("GHALBIT", "WiFi Direct discovery failed $reason") }
                })
            } catch (e: SecurityException) {
                Log.e("GHALBIT", "WiFi Direct discovery blocked by permission", e)
            }
        }
    }

    fun connectToDevice(device: WifiP2pDevice) {
        val activeChannel = channel ?: return
        val config = WifiP2pConfig().apply { deviceAddress = device.deviceAddress }
        try {
            manager?.connect(activeChannel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {}
                override fun onFailure(reason: Int) {}
            })
        } catch (e: SecurityException) {
            Log.e("GHALBIT", "WiFi Direct connect blocked by permission", e)
        }
    }

    fun cleanup() {
        val activeChannel = channel

        if (activeChannel != null) {
            try {
                manager?.stopPeerDiscovery(activeChannel, null)
            } catch (e: Exception) {
                Log.e("GHALBIT", "WiFi Direct stop discovery failed", e)
            }
        }

        if (receiverRegistered) {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.e("GHALBIT", "WiFi Direct receiver unregister failed", e)
            } finally {
                receiverRegistered = false
            }
        }
    }
}
