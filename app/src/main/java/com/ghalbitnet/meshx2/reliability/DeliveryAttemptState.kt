package com.ghalbitnet.meshx2.reliability

enum class DeliveryAttemptState {
    CREATED,
    QUEUED,
    RELAYED,
    ACKNOWLEDGED,
    EXPIRED,
    ABANDONED
}
