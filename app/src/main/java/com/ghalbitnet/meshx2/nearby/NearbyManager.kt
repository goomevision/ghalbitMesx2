package com.ghalbitnet.meshx2.nearby

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.discovery.DiscoveryManager
import com.ghalbitnet.meshx2.model.MeshNode
import com.ghalbitnet.meshx2.security.KeyStoreManager
import com.ghalbitnet.meshx2.network.MeshSocketServer
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*

class NearbyManager(context: Context, private val keyStore: KeyStoreManager) {
    private val connectionClient = Nearby.getConnectionsClient(context)
    private val serviceId = "com.ghalbitnet.meshx2"
    private val localEndpointName = "GhalbitX2-${android.os.Build.MODEL.replace(" ", "_")}"

    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            try {
                connectionClient.acceptConnection(endpointId, payloadCallback)
                    .addOnFailureListener { e ->
                        Log.e("GHALBIT", "Nearby accept connection failed", e)
                    }
            } catch (e: SecurityException) {
                Log.e("GHALBIT", "Nearby accept blocked by permission", e)
            }
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                DiscoveryManager.addNode(MeshNode(name = "Nearby-$endpointId", ipAddress = "nearby:$endpointId", online = true))
                val pubKey = keyStore.publicKeyBase64
                sendBytes(endpointId, pubKey.toByteArray())
            }
        }
        override fun onDisconnected(endpointId: String) {}
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val data = String(payload.asBytes()!!)
                // Jika data terlihat seperti public key, simpan
                if (data.length > 30 && data.matches(Regex("^[A-Za-z0-9+/=]+$"))) {
                    keyStore.storePeerKey("nearby:$endpointId", data)
                    DiscoveryManager.addNode(MeshNode(name = "Nearby-$endpointId", ipAddress = "nearby:$endpointId", online = true, publicKey = data))
                } else {
                    // Data paket mesh; injeksikan ke MeshSocketServer
                    MeshSocketServer.injectPacket(data, "nearby:$endpointId")
                }
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    init {
        try {
            connectionClient.startAdvertising(
                localEndpointName,
                serviceId,
                lifecycleCallback,
                AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
            )
                .addOnSuccessListener { Log.d("GHALBIT", "Nearby advertising started") }
                .addOnFailureListener { e -> Log.e("GHALBIT", "Nearby advertising failed", e) }

            connectionClient.startDiscovery(
                serviceId,
                object : EndpointDiscoveryCallback() {
                    override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                        try {
                            connectionClient.requestConnection(
                                localEndpointName,
                                endpointId,
                                lifecycleCallback
                            )
                                .addOnFailureListener { e ->
                                    Log.e("GHALBIT", "Nearby request connection failed", e)
                                }
                        } catch (e: SecurityException) {
                            Log.e("GHALBIT", "Nearby request blocked by permission", e)
                        }
                    }

                    override fun onEndpointLost(endpointId: String) {}
                },
                DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
            )
                .addOnSuccessListener { Log.d("GHALBIT", "Nearby discovery started") }
                .addOnFailureListener { e -> Log.e("GHALBIT", "Nearby discovery failed", e) }
        } catch (e: SecurityException) {
            Log.e("GHALBIT", "Nearby startup blocked by permission", e)
        } catch (e: Exception) {
            Log.e("GHALBIT", "Nearby startup failed", e)
        }
    }

    fun sendPacket(endpointId: String, packet: String) {
        sendBytes(endpointId, packet.toByteArray())
    }

    private fun sendBytes(endpointId: String, bytes: ByteArray) {
        try {
            connectionClient.sendPayload(endpointId, Payload.fromBytes(bytes))
                .addOnFailureListener { e ->
                    Log.e("GHALBIT", "Nearby send payload failed", e)
                }
        } catch (e: SecurityException) {
            Log.e("GHALBIT", "Nearby send blocked by permission", e)
        }
    }

    fun stop() {
        try {
            connectionClient.stopAllEndpoints()
        } catch (e: Exception) {
            Log.e("GHALBIT", "Nearby stop failed", e)
        }
    }
}
