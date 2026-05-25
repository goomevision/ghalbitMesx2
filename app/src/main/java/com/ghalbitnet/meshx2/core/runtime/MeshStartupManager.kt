package com.ghalbitnet.meshx2.core.runtime

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.ghalbitnet.meshx2.discovery.UdpDiscovery
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.model.SecurePacket
import com.ghalbitnet.meshx2.network.MeshSocketServer
import com.ghalbitnet.meshx2.security.KeyStoreManager
import com.ghalbitnet.meshx2.service.MeshForegroundService
import com.ghalbitnet.meshx2.wifi.WifiDirectManager
import com.ghalbitnet.meshx2.wireguard.WireGuardMeshManager
import com.ghalbitnet.meshx2.nearby.NearbyManager
import com.ghalbitnet.meshx2.core.recovery.MeshAutoRecovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object MeshStartupManager {

    interface MeshStartupListener {
        fun onStatus(message: String)
        fun onError(message: String, throwable: Throwable? = null)
        fun onNodeDiscovered(summary: String)
    }

    data class Session(
        val wgManager: WireGuardMeshManager?,
        val wifiDirectManager: WifiDirectManager?,
        val nearbyManager: NearbyManager?,
        val discoveryHeartbeatJob: Job?
    )

    data class StartParams(
        val context: Context,
        val applicationContext: Context,
        val scope: CoroutineScope,
        val keyStore: KeyStoreManager,
        val localPeerId: String,
        val wireGuardAddress: String,
        val onBroadcastLocalNode: () -> Unit,
        val onPacket: (MeshPacket) -> Unit,
        val onSecurePacket: (SecurePacket) -> Unit,
        val onNodeFound: (String, String, String, Boolean, Boolean) -> Unit
    )

    fun start(
        params: StartParams,
        listener: MeshStartupListener
    ): Session {
        listener.onStatus("Menyiapkan layanan mesh...")
        LightweightMeshSupervisor.start(params.applicationContext)
        MeshHeartbeatTicker.start()
        startForegroundMeshService(params.context, listener)

        val wgManager = WireGuardMeshManager(params.context)
        params.scope.launch {
            runCatching {
                wgManager.startMesh(params.wireGuardAddress)
            }.onFailure {
                listener.onError("WireGuard mesh gagal dimulai.", it)
            }
        }

        UdpDiscovery.init(params.context, params.keyStore)
        runCatching {
            params.onBroadcastLocalNode()
        }.onFailure {
            listener.onError("Broadcast node awal gagal.", it)
        }

        val discoveryHeartbeatJob =
            params.scope.launch {
                while (true) {
                    try {
                        params.onBroadcastLocalNode()
                    } catch (error: Exception) {
                        Log.e("GHALBIT", "Discovery heartbeat failed", error)
                        listener.onError("Discovery heartbeat gagal.", error)
                    }
                    delay(10_000L)
                }
            }

        MeshSocketServer.appContext = params.applicationContext
        MeshSocketServer.localPeerId = params.localPeerId
        MeshSocketServer.start(
            onPacket = params.onPacket,
            onSecure = params.onSecurePacket
        )

        UdpDiscovery.listen { peerId, ip, pubKey, gateway, relay ->
            params.onNodeFound(peerId, ip, pubKey, gateway, relay)
            listener.onNodeDiscovered("$peerId@$ip")
        }

        val wifiDirectManager = WifiDirectManager(params.context)
        val nearbyManager = NearbyManager(params.context, params.keyStore)

        startAutoRecovery(params.onBroadcastLocalNode)
        listener.onStatus("Mesh aktif. Menunggu node terdekat...")

        return Session(
            wgManager = wgManager,
            wifiDirectManager = wifiDirectManager,
            nearbyManager = nearbyManager,
            discoveryHeartbeatJob = discoveryHeartbeatJob
        )
    }

    fun stop(
        context: Context,
        session: Session?
    ) {
        session?.nearbyManager?.stop()
        session?.wifiDirectManager?.cleanup()
        MeshSocketServer.stop()
        UdpDiscovery.stop()
        context.stopService(Intent(context, MeshForegroundService::class.java))
        session?.discoveryHeartbeatJob?.cancel()
        session?.wgManager?.stop()
        MeshHeartbeatTicker.stop()
        LightweightMeshSupervisor.stop()
        MeshAutoRecovery.stop()
    }

    fun ensureAutoRecovery(
        onBroadcastLocalNode: () -> Unit
    ) {
        startAutoRecovery(onBroadcastLocalNode)
    }

    private fun startAutoRecovery(
        onBroadcastLocalNode: () -> Unit
    ) {
        MeshAutoRecovery.start {
            try {
                onBroadcastLocalNode()
            } catch (_: Exception) {
            }

            try {
                MeshRuntimeState.heartbeat()
            } catch (_: Exception) {
            }
        }
    }

    private fun startForegroundMeshService(
        context: Context,
        listener: MeshStartupListener
    ) {
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MeshForegroundService::class.java)
            )
        } catch (error: Exception) {
            Log.e("GHALBIT", "Foreground service start failed", error)
            listener.onError("Foreground mesh service gagal dimulai.", error)
        }
    }
}

