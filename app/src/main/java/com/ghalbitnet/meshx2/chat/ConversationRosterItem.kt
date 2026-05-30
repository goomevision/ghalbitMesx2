package com.ghalbitnet.meshx2.chat

data class ConversationRosterItem(
    val legacyChatId: String,
    val globalId: String?,
    val displayName: String,
    val walletAddress: String?,
    val lastKnownIp: String?,
    val lastSeen: Long?,
    val publicKey: String? = null
)
