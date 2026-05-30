package com.ghalbitnet.meshx2.chat

import com.ghalbitnet.meshx2.identity.GhalbitIdentityRecord

data class ConversationOwnershipHint(
    val legacyChatId: String,
    val globalId: String? = null,
    val publicKey: String? = null,
    val walletAddress: String? = null,
    val canonicalDisplayName: String? = null,
    val lastKnownIp: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toIdentityRecord(): GhalbitIdentityRecord {
        return GhalbitIdentityRecord(
            globalId = globalId ?: legacyChatId,
            publicKey = publicKey,
            walletAddress = walletAddress,
            displayName = canonicalDisplayName,
            lastKnownIp = lastKnownIp,
            lastSeen = updatedAt
        )
    }
}
