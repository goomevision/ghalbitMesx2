package com.ghalbitnet.meshx2.network

import android.util.Log
import com.ghalbitnet.meshx2.model.MeshNode
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.stats.MeshStatistics

class OutboundMeshActionHandler(
    private val localPeerId: String,
    private val nodeProvider: () -> List<MeshNode>,
    private val listener: OutboundMeshActionListener
) {

    interface OutboundMeshActionListener {
        fun onActionStatus(message: String)
        fun onActionError(message: String, throwable: Throwable? = null)
        fun onSosSent(summary: String)
        fun onCallSignalSent(summary: String)
    }

    fun sendSos() {
        listener.onActionStatus("SOS SIGNAL SENT")

        val nodes = nodeProvider()
        val sosPacket =
            MeshPacket(
                packetId = "SOS-" + System.currentTimeMillis(),
                source = localPeerId,
                destination = "BROADCAST",
                type = "SOS",
                payload = "EMERGENCY"
            )

        var sentCount = 0
        nodes.forEach { node ->
            try {
                // TODO unified identity: outbound action should resolve destination by globalId.
                MeshStatistics.sentPacket("SOS")
                MeshSocketClient.send(
                    node.ipAddress,
                    sosPacket
                )
                sentCount += 1
            } catch (error: Exception) {
                Log.e("GHALBIT", "SOS failed to ${node.name}", error)
                listener.onActionError("SOS failed to ${node.name}", error)
            }
        }

        listener.onSosSent("SOS selesai dikirim ke $sentCount node yang tersedia.")
    }
}

