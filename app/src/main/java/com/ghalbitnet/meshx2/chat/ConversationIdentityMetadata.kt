package com.ghalbitnet.meshx2.chat

import com.ghalbitnet.meshx2.identity.GhalbitIdentityRecord

data class ConversationIdentityMetadata(
    val chatId: String,
    val globalId: String? = null,
    val publicKey: String? = null,
    val publicKeyHash: String? = null,
    val walletAddress: String? = null,
    val canonicalDisplayName: String? = null,
    val lastSeen: Long? = null,
    val routeHint: String? = null,
    val verificationStatus: PeerVerificationStatus = PeerVerificationStatus.STALE,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toIdentityRecord(): GhalbitIdentityRecord? {
        if (
            globalId.isNullOrBlank() &&
            publicKey.isNullOrBlank() &&
            walletAddress.isNullOrBlank() &&
            canonicalDisplayName.isNullOrBlank()
        ) {
            return null
        }

        return GhalbitIdentityRecord(
            globalId = globalId ?: chatId,
            publicKey = publicKey,
            walletAddress = walletAddress,
            displayName = canonicalDisplayName,
            lastKnownIp = routeHint,
            lastSeen = lastSeen ?: updatedAt
        )
    }
}
