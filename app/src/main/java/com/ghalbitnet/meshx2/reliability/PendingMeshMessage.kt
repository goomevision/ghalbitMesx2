package com.ghalbitnet.meshx2.reliability

data class PendingMeshMessage(
    val messageId: String,
    val legacyChatId: String,
    val canonicalGlobalId: String? = null,
    val retryCount: Int = 0,
    val createdAt: Long,
    val expiresAt: Long?,
    val relayHopCount: Int = 0,
    val state: DeliveryAttemptState = DeliveryAttemptState.CREATED
)
