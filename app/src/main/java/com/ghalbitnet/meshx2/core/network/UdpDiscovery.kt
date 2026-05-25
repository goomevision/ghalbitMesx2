package com.ghalbitnet.meshx2.core.network

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

object UdpDiscovery {

    private const val PORT = 45454
    private const val TAG = "UdpDiscovery"

    fun startDiscovery(myId: String) {

        thread {

            try {

                val socket = DatagramSocket(PORT)
                socket.broadcast = true

                Log.d(TAG, "UDP Discovery Started")

                while (true) {

                    val buffer = ByteArray(1024)

                    val packet = DatagramPacket(
                        buffer,
                        buffer.size
                    )

                    socket.receive(packet)

                    val msg = String(
                        packet.data,
                        0,
                        packet.length
                    )

                    if (msg.startsWith("GHALBIT:")) {

                        val peerId =
                            msg.removePrefix("GHALBIT:")

                        val ip =
                            packet.address.hostAddress ?: ""

                        if (peerId != myId) {

                            PeerManager.addPeer(
                                peerId,
                                ip
                            )

                            Log.d(
                                TAG,
                                "Peer: $peerId at $ip"
                            )
                        }
                    }
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Discovery error: ${e.message}"
                )
            }
        }
    }

    fun broadcast(myId: String) {

        thread {

            try {

                val socket = DatagramSocket()

                socket.broadcast = true

                val data =
                    "GHALBIT:$myId".toByteArray()

                val packet = DatagramPacket(
                    data,
                    data.size,
                    InetAddress.getByName(
                        "255.255.255.255"
                    ),
                    PORT
                )

                socket.send(packet)

                socket.close()

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Broadcast error: ${e.message}"
                )
            }
        }
    }
}
