package com.ghalbitnet.meshx2.call

interface AiTranscriptTransport {
    fun send(packet: AiTranscriptPacket): Boolean
    fun receive(raw: ByteArray): AiTranscriptPacket?
}
