package com.ghalbitnet.meshx2.reliability

enum class DeliveryAcknowledgmentState {
    CREATED,
    LOCALLY_QUEUED,
    RELAY_ACCEPTED,
    RELAY_FORWARDED,
    DESTINATION_RECEIVED,
    ACKNOWLEDGED,
    PARTIALLY_DELIVERED,
    EXPIRED,
    CUSTODY_LOST,
    FAILED
}
