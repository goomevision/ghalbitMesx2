package com.ghalbitnet.meshx2.core.network

import android.util.Log

object MultiHopRouter {

    private const val TAG =
        "MultiHopRouter"

    fun forward(packet: MeshPacket) {

        if (packet.ttl <= 0) {

            Log.d(TAG, "TTL expired")
            return
        }

        PeerManager
            .getAllPeers()
            .forEach { (peerId, ip) ->

                if (peerId != packet.source) {

                    val forwarded =
                        packet.copy(
                            ttl = packet.ttl - 1
                        )

                    TcpMeshClient.send(
                        ip,
                        forwarded
                    )

                    Log.d(
                        TAG,
                        "Forwarded to $peerId"
                    )
                }
            }
    }
}
