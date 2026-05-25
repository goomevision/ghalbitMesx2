package com.ghalbitnet.meshx2.core.network

import android.util.Log

object RelayEngine {

    private const val TAG = "RelayEngine"

    fun relay(packet: MeshPacket) {

        if (packet.ttl <= 0) {

            Log.d(TAG, "Packet expired")
            return
        }

        val newPacket =
            packet.copy(
                ttl = packet.ttl - 1
            )

        PeerManager
            .getAllPeers()
            .forEach { (peerId, ip) ->

                Log.d(
                    TAG,
                    "Relay to $peerId at $ip"
                )

                // nanti kirim TCP/UDP disini
            }
    }
}
