package com.ghalbitnet.meshx2.reliability

data class DeliveryReceipt(
    val messageId: String,
    val legacyChatId: String,
    val canonicalGlobalId: String? = null,
    val relayHopCount: Int = 0,
    val acknowledgedAt: Long? = null,
    val expiresAt: Long? = null,
    val state: DeliveryAcknowledgmentState =
        DeliveryAcknowledgmentState.CREATED
)
