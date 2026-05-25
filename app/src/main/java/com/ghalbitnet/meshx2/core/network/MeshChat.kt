package com.ghalbitnet.meshx2.core.network

import android.util.Log
import java.util.UUID

object MeshChat {

    private const val TAG = "MeshChat"

    fun sendMessage(
        myId: String,
        destination: String,
        message: String
    ) {

        val ip =
            PeerManager.getPeerIp(destination)

        if (ip == null) {

            Log.e(
                TAG,
                "Peer not found"
            )

            return
        }

        val packet =
            MeshPacket(
                id = UUID.randomUUID().toString(),
                source = myId,
                destination = destination,
                type = "CHAT",
                payload = message
            )

        TcpMeshClient.send(ip, packet)
    }
}
