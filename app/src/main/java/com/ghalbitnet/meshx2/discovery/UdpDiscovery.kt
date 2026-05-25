package com.ghalbitnet.meshx2.discovery

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.access.AccessHandshakeManager
import com.ghalbitnet.meshx2.access.NetworkAccessPolicy
import com.ghalbitnet.meshx2.access.NodeIdentityManager
import com.ghalbitnet.meshx2.access.UnauthorizedDeviceDetector
import com.ghalbitnet.meshx2.security.KeyStoreManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object UdpDiscovery {
    private const val PORT = 45454
    @Volatile
    private var running = false
    private var keyStore: KeyStoreManager? = null
    private var appContext: Context? = null
    private var listenerSocket: DatagramSocket? = null

    fun init(context: Context, keyStore: KeyStoreManager) {
        this.keyStore = keyStore
        this.appContext = context.applicationContext
    }

    fun broadcastNode(
        nodeName: String,
        gateway: Boolean = false,
        relay: Boolean = true
    ) {
        val context = appContext ?: return
        val helloAuth = NodeIdentityManager.buildHelloAuth(context, gateway, relay)

        Thread {
            try {
                DatagramSocket().use { socket ->
                    socket.broadcast = true

                    val msg =
                        listOf(
                            "GHALBITX2_AUTH",
                            helloAuth.nodeId,
                            helloAuth.publicKey,
                            helloAuth.walletAddress,
                            helloAuth.appVersion,
                            helloAuth.timestamp.toString(),
                            helloAuth.nonce,
                            helloAuth.signature,
                            flag(helloAuth.gateway),
                            flag(helloAuth.relay)
                        ).joinToString("|")

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

                Log.d("GHALBIT", "UDP broadcast HELLO_AUTH: ${helloAuth.nodeId}")
            } catch (e: Exception) {
                Log.e("GHALBIT", "UDP broadcast error", e)
            }
        }.start()
    }

    fun listen(onNodeFound: (String, String, String, Boolean, Boolean) -> Unit) {
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
                    if (msg.startsWith("GHALBITX2_AUTH|")) {
                        val parts = msg.split('|')
                        val ip = p.address?.hostAddress ?: "0.0.0.0"
                        val hello =
                            NodeIdentityManager.HelloAuth(
                                nodeId = parts.getOrElse(1) { "GX-UNKNOWN" },
                                publicKey = parts.getOrElse(2) { "" },
                                walletAddress = parts.getOrElse(3) { "" },
                                appVersion = parts.getOrElse(4) { "" },
                                timestamp = parts.getOrElse(5) { "0" }.toLongOrNull() ?: 0L,
                                nonce = parts.getOrElse(6) { "" },
                                signature = parts.getOrElse(7) { "" },
                                gateway = parseFlag(parts.getOrElse(8) { "0" }),
                                relay = parseFlag(parts.getOrElse(9) { "1" })
                            )
                        val context = appContext
                        if (context == null) {
                            UnauthorizedDeviceDetector.markUnknown(ip, "Context auth belum siap.")
                            continue
                        }
                        val result =
                            AccessHandshakeManager.authorizeIncoming(
                                context = context,
                                hello = hello,
                                ipAddress = ip,
                                port = NetworkAccessPolicy.DEFAULT_MESH_SOCKET_PORT
                            )
                        if (result.status == NetworkAccessPolicy.AuthStatus.AUTHORIZED) {
                            Log.d("GHALBIT", "UDP received HELLO_AUTH: ${hello.nodeId}@$ip")
                            onNodeFound(hello.nodeId, ip, hello.publicKey, hello.gateway, hello.relay)
                        } else {
                            UnauthorizedDeviceDetector.block(
                                hello.nodeId,
                                result.detail
                            )
                        }
                    } else if (msg.startsWith("GHALBITX2:")) {
                        val ip = p.address?.hostAddress ?: "0.0.0.0"
                        UnauthorizedDeviceDetector.markUnknown(ip, "HELLO lama tanpa auth.")
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
}
