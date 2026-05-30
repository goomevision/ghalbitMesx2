package com.ghalbitnet.meshx2.identity

import com.ghalbitnet.meshx2.chat.ConversationIdentityMetadata

data class ResolvedPeerIdentity(
    val legacyChatId: String,
    val peerName: String,
    val peerIp: String,
    val globalId: String? = null,
    val publicKey: String? = null,
    val walletAddress: String? = null,
    val displayName: String? = null,
    val primaryLabel: String,
    val secondaryLabel: String? = null,
    val resolutionSource: String? = null,
    val resolvedAt: Long = System.currentTimeMillis()
) {
    fun toIdentityRecord(): GhalbitIdentityRecord {
        return GhalbitIdentityRecord(
            globalId = globalId ?: legacyChatId,
            publicKey = publicKey,
            walletAddress = walletAddress,
            displayName = displayName,
            lastKnownIp = peerIp.ifBlank { null },
            lastSeen = resolvedAt
        )
    }

    fun toConversationMetadata(): ConversationIdentityMetadata {
        return ConversationIdentityMetadata(
            chatId = legacyChatId,
            globalId = globalId,
            publicKey = publicKey,
            walletAddress = walletAddress,
            canonicalDisplayName = displayName,
            updatedAt = resolvedAt
        )
    }
}
