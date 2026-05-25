package com.ghalbitnet.meshx2.routing

import com.ghalbitnet.meshx2.core.log.MeshLogger
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.network.MeshSocketClient
import com.ghalbitnet.meshx2.stats.MeshStatistics

object PacketForwarder {

    fun forward(
        packet: MeshPacket
    ): Boolean {

        return try {

            val newPayload =
                PacketTtlManager.decreasePayloadTtl(
                    packet.payload
                ) ?: return false

            val route =
                RouteTable.getRoute(
                    packet.destination
                ) ?: return false

            val forwardedPacket =
                packet.copy(
                    payload = newPayload
                )

            MeshSocketClient.send(
                route.nextHop,
                forwardedPacket
            )

            MeshStatistics.forwardedPacket(
                packet.type
            )

            MeshLogger.i(
                "FORWARD",
                "packet forwarded to ${route.nextHop}"
            )

            true

        } catch (e: Exception) {

            MeshLogger.e(
                "FORWARD",
                "forward failed",
                e
            )

            false
        }
    }
}
