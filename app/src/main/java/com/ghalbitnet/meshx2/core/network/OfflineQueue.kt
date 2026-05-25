package com.ghalbitnet.meshx2.core.network

object OfflineQueue {

    private val queue =
        mutableListOf<MeshPacket>()

    fun add(packet: MeshPacket) {

        queue.add(packet)
    }

    fun getAll(): List<MeshPacket> {

        return queue.toList()
    }

    fun remove(packet: MeshPacket) {

        queue.remove(packet)
    }
}
