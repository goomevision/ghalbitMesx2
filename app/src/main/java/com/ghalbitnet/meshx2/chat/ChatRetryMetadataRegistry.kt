package com.ghalbitnet.meshx2.chat

import java.util.concurrent.ConcurrentHashMap

data class ChatRetryMetadata(
    val peerGlobalId: String? = null,
    val peerPublicKey: String? = null,
    val peerWalletAddress: String? = null,
    val peerDisplayName: String? = null
)

object ChatRetryMetadataRegistry {

    private val metadataByPacketId =
        ConcurrentHashMap<String, ChatRetryMetadata>()

    fun put(
        packetId: String,
        metadata: ChatRetryMetadata
    ) {
        metadataByPacketId[packetId] = metadata
    }

    fun get(packetId: String): ChatRetryMetadata? {
        return metadataByPacketId[packetId]
    }

    fun remove(packetId: String) {
        metadataByPacketId.remove(packetId)
    }

    fun count(): Int {
        return metadataByPacketId.size
    }
}
