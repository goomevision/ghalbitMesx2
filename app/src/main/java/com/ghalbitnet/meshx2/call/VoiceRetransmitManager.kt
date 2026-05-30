package com.ghalbitnet.meshx2.call

import java.util.LinkedHashMap

class VoiceRetransmitManager(private val capacity: Int = 64) {
    private val recentPackets =
        object : LinkedHashMap<Int, VoicePacket>(capacity, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, VoicePacket>?): Boolean {
                return size > capacity
            }
        }

    fun remember(packet: VoicePacket) {
        recentPackets[packet.sequence] = packet
    }

    fun packetsForAck(ack: VoiceAck): List<VoicePacket> {
        return ack.missingSequences.mapNotNull(recentPackets::get)
    }

    fun clearSession() {
        recentPackets.clear()
    }
}
