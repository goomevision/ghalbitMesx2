package com.ghalbitnet.meshx2.core.routing

import com.ghalbitnet.meshx2.packet.MeshPacket

object MeshRelayEngine {

    fun relay(packet: MeshPacket) {

        if (packet.ttl <= 0) {

            println("Packet dropped")
            return
        }

        val nextHop =
            RoutingTable.getRoute(
                packet.toNode
            )

        println(
            "Relaying packet to: $nextHop"
        )
    }
}