package com.ghalbitnet.meshx2.reliability

enum class DelayedSyncState {
    IDLE,
    PENDING,
    DEFERRED,
    RECONNECTING,
    PARTIALLY_RECOVERED,
    EXPIRED
}
