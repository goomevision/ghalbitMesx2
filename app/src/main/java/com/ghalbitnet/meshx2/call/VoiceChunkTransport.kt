package com.ghalbitnet.meshx2.call

interface VoiceChunkTransport {
    fun send(packet: VoicePacket): Boolean
    fun receive(raw: ByteArray): VoicePacket?
}
