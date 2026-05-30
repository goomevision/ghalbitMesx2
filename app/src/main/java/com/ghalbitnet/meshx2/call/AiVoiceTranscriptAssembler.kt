package com.ghalbitnet.meshx2.call

class AiVoiceTranscriptAssembler {
    private val items = linkedMapOf<Int, AiTranscriptPacket>()

    fun add(packet: AiTranscriptPacket) {
        items.putIfAbsent(packet.sequenceNumber, packet)
    }

    fun ordered(): List<AiTranscriptPacket> = items.toSortedMap().values.toList()
}
