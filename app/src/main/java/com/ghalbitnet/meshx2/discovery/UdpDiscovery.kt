package com.ghalbitnet.meshx2.discovery

import android.util.Log
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.security.KeyStoreManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.MessageDigest

object UdpDiscovery {
    private const val PORT = 45454
    private const val TAG = "GHALBIT-ROUTE"
    @Volatile
    private var running = false
    private var keyStore: KeyStoreManager? = null
    private var listenerSocket: DatagramSocket? = null
    private var localPeerId: String = ""
    private var localGlobalId: String? = null
    private var localPublicKeyHash: String? = null

    data class DiscoveryHelloPacket(
        val sourceNodeId: String,
        val sourceIp: String,
        val publicKey: String,
        val gateway: Boolean,
        val relay: Boolean,
        val sourceGlobalId: String? = null,
        val sourcePublicKeyHash: String? = null
    )

    fun init(keyStore: KeyStoreManager) { this.keyStore = keyStore }

    fun setLocalIdentity(peerId: String, publicKey: String?) {
        localPeerId = peerId
        localGlobalId = publicKey?.takeIf { it.isNotBlank() }?.let { GlobalMeshIdentityManager.buildGlobalId(it) }
        localPublicKeyHash = hashPublicKey(publicKey)
    }

    fun broadcastNode(
        nodeName: String,
        gateway: Boolean = false,
        relay: Boolean = true
    ) {
        val pubKey = keyStore?.publicKeyBase64 ?: ""

        Thread {
            try {
                DatagramSocket().use { socket ->
                    socket.broadcast = true

                    val msg =
                        "GHALBITX2:$nodeName:$pubKey:${flag(gateway)}:${flag(relay)}:${localGlobalId.orEmpty()}:${localPublicKeyHash.orEmpty()}"

                    val data =
                        msg.toByteArray()

                    val packet =
                        DatagramPacket(
                            data,
                            data.size,
                            InetAddress.getByName("255.255.255.255"),
                            PORT
                        )

                    socket.send(packet)
                }

                Log.d("GHALBIT", "UDP broadcast HELLO: $nodeName")
            } catch (e: Exception) {
                Log.e("GHALBIT", "UDP broadcast error", e)
            }
        }.start()
    }

    fun listen(onNodeFound: (DiscoveryHelloPacket) -> Unit) {
        if (running) return

        running = true

        Thread {
            try {
                val socket =
                    DatagramSocket(PORT)

                listenerSocket =
                    socket

                val buf = ByteArray(2048)

                Log.d("GHALBIT", "UDP listener started on port $PORT")

                while (running) {
                    val p = DatagramPacket(buf, buf.size)
                    socket.receive(p)
                    val msg = String(p.data, 0, p.length)
                    if (msg.startsWith("GHALBITX2:")) {
                        val parts = msg.removePrefix("GHALBITX2:").split(":")
                        val name = parts.getOrElse(0) { "unknown" }
                        val ip = p.address?.hostAddress ?: "0.0.0.0"
                        val pubKey = parts.getOrElse(1) { "" }
                        val gateway = parseFlag(parts.getOrElse(2) { "0" })
                        val relay = parseFlag(parts.getOrElse(3) { "1" })
                        val sourceGlobalId = parts.getOrElse(4) { "" }.ifBlank { null }
                        val sourcePublicKeyHash = parts.getOrElse(5) { "" }.ifBlank { null } ?: hashPublicKey(pubKey)
                        val helloPacket =
                            DiscoveryHelloPacket(
                                sourceNodeId = name,
                                sourceIp = ip,
                                publicKey = pubKey,
                                gateway = gateway,
                                relay = relay,
                                sourceGlobalId = sourceGlobalId,
                                sourcePublicKeyHash = sourcePublicKeyHash
                            )
                        val selfDecision = selfDecision(helloPacket)
                        if (selfDecision.first) {
                            Log.d(
                                "GHALBIT-DISCOVERY-RX",
                                "source=$name ip=$ip local=$localPeerId localGlobal=${localGlobalId ?: "-"} localPublicKeyHash=${localPublicKeyHash ?: "-"} sourceGlobal=${sourceGlobalId ?: "-"} sourcePublicKeyHash=${sourcePublicKeyHash ?: "-"} isSelf=true accepted=false reasonIgnored=${selfDecision.second}"
                            )
                            Log.d(TAG, "Ignored self UDP HELLO: $name@$ip")
                            continue
                        }

                        Log.d(
                            "GHALBIT-DISCOVERY-RX",
                            "source=$name ip=$ip local=$localPeerId localGlobal=${localGlobalId ?: "-"} localPublicKeyHash=${localPublicKeyHash ?: "-"} sourceGlobal=${sourceGlobalId ?: "-"} sourcePublicKeyHash=${sourcePublicKeyHash ?: "-"} isSelf=false accepted=true reasonIgnored=-"
                        )
                        Log.d("GHALBIT", "UDP received HELLO: $name@$ip")
                        onNodeFound(helloPacket)
                    }
                }
            } catch (e: Exception) {
                if (running) {
                    Log.e("GHALBIT", "UDP listen error", e)
                }
            } finally {
                try {
                    listenerSocket?.close()
                } catch (_: Exception) {
                }

                listenerSocket = null
                running = false
            }
        }.start()
    }

    fun stop() {
        running = false

        try {
            listenerSocket?.close()
        } catch (_: Exception) {
        }

        listenerSocket = null
    }

    private fun flag(value: Boolean): String {
        return if (value) "1" else "0"
    }

    private fun parseFlag(value: String): Boolean {
        return value == "1" || value.equals("true", ignoreCase = true)
    }

    private fun selfDecision(packet: DiscoveryHelloPacket): Pair<Boolean, String> {
        if (localPeerId.isNotBlank() && packet.sourceNodeId == localPeerId) {
            return true to "sameNodeId"
        }
        if (!localPublicKeyHash.isNullOrBlank() &&
            !packet.sourcePublicKeyHash.isNullOrBlank() &&
            localPublicKeyHash == packet.sourcePublicKeyHash
        ) {
            return true to "samePublicKeyHash"
        }
        if (!localGlobalId.isNullOrBlank() &&
            !packet.sourceGlobalId.isNullOrBlank() &&
            localGlobalId == packet.sourceGlobalId
        ) {
            return true to "sameGlobalId"
        }
        return false to "accepted"
    }

    private fun hashPublicKey(publicKey: String?): String? {
        if (publicKey.isNullOrBlank()) return null
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKey.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
