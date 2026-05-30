package com.ghalbitnet.meshx2.chat

data class LiveContactItem(
    val chatId: String,
    val globalId: String? = null,
    val publicKey: String? = null,
    val publicKeyHash: String? = null,
    val displayName: String,
    val walletAddress: String? = null,
    val lastSeen: Long? = null,
    val routeHint: String? = null,
    val verificationStatus: PeerVerificationStatus = PeerVerificationStatus.STALE,
    val isSaved: Boolean = false,
    val isLive: Boolean = false,
    val isOffline: Boolean = false
)
