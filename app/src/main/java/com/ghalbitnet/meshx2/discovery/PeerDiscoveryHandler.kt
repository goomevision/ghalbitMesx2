package com.ghalbitnet.meshx2.discovery

import android.content.Context
import com.ghalbitnet.meshx2.core.network.PeerAddressRegistry
import com.ghalbitnet.meshx2.core.node.NodeStatusManager
import com.ghalbitnet.meshx2.model.MeshNode
import com.ghalbitnet.meshx2.security.CryptoEngine
import com.ghalbitnet.meshx2.security.KeyStoreManager
import com.ghalbitnet.meshx2.routing.RouteDiscovery

class PeerDiscoveryHandler(
    private val context: Context,
    private val appContext: Context,
    private val keyStore: KeyStoreManager,
    private val listener: PeerDiscoveryListener
) {

    interface PeerDiscoveryListener {
        fun onPeerDiscovered(summary: String)
        fun onPeerUpdated(summary: String)
        fun onDiscoveryStatus(message: String)
        fun onDiscoveryError(message: String, throwable: Throwable? = null)
    }

    fun handleDiscoveredNode(
        peerId: String,
        ip: String,
        pubKey: String,
        gateway: Boolean,
        relay: Boolean
    ) {
        try {
            // TODO unified identity: resolve discovered peer by globalId, not IP/display name.
            val peerKeyChanged =
                pubKey.isNotEmpty() &&
                    keyStore.isPeerKeyChanged(peerId, pubKey)

            if (pubKey.isNotEmpty()) {
                keyStore.storePeerKey(peerId, pubKey)
                keyStore.storePeerAddress(peerId, ip)
            }
            PeerAddressRegistry.register(appContext, peerId, ip)
            if (pubKey.isNotEmpty()) {
                PeerAddressRegistry.register(appContext, pubKey, ip)
            }

            val trustScore =
                if (peerKeyChanged) 0 else 50

            val discoveredNode =
                MeshNode(
                    name = peerId,
                    ipAddress = ip,
                    publicKey = pubKey,
                    trusted = trustScore,
                    online = true,
                    gateway = gateway,
                    relay = relay
                )

            NodeStatusManager.upsertNode(discoveredNode)
            DiscoveryManager.addNode(discoveredNode)
            RouteDiscovery.rememberDirectRoute(
                destinationPeerId = peerId,
                destinationIp = ip,
                trustScore = trustScore
            )

            listener.onPeerDiscovered("NODE $peerId @ $ip")
            if (peerKeyChanged) {
                val fingerprint = CryptoEngine.fingerprint(pubKey)
                listener.onPeerUpdated("WARNING: KEY CHANGED for $peerId ($fingerprint)")
                listener.onDiscoveryStatus("Peer key changed: $peerId")
            }
        } catch (error: Exception) {
            listener.onDiscoveryError("Discovery handling failed for $peerId@$ip", error)
        }
    }
}

