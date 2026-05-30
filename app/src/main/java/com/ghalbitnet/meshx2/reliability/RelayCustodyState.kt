package com.ghalbitnet.meshx2.reliability

data class RelayCustodyState(
    val messageId: String,
    val relayNodeId: String?,
    val relayHopCount: Int,
    val createdAt: Long,
    val expiresAt: Long?,
    val state: DeliveryAttemptState
)
