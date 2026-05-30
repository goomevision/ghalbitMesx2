package com.ghalbitnet.meshx2.reliability

data class RelayAcknowledgment(
    val messageId: String,
    val legacyChatId: String,
    val canonicalGlobalId: String? = null,
    val relayHopCount: Int = 0,
    val acknowledgedAt: Long? = null,
    val expiresAt: Long? = null,
    val state: DeliveryAcknowledgmentState =
        DeliveryAcknowledgmentState.RELAY_ACCEPTED
)
