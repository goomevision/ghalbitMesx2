package com.ghalbitnet.meshx2.call

data class CallSignalEvent(
    val eventId: String,
    val callId: String,
    val type: String,
    val peerName: String,
    val nodeId: String,
    val globalId: String?,
    val publicKey: String?,
    val publicKeyHash: String?,
    val walletAddress: String?,
    val displayName: String?,
    val routeHint: String?,
    val transportIp: String?,
    val localNodeId: String,
    val localGlobalId: String?,
    val localPublicKeyHash: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    val nextRetryAt: Long = System.currentTimeMillis()
)
