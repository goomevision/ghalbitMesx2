package com.ghalbitnet.meshx2.reliability

data class DeferredSyncWindow(
    val peerReference: String,
    val state: DelayedSyncState,
    val createdAt: Long,
    val expiresAt: Long?,
    val lowBandwidth: Boolean = false
)
