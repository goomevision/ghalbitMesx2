package com.ghalbitnet.meshx2.identity

data class GhalbitIdentityRecord(
    val globalId: String,
    val publicKey: String?,
    val walletAddress: String?,
    val displayName: String?,
    val lastKnownIp: String?,
    val lastSeen: Long,
    val trustScore: Int = 0,
    val relayCapable: Boolean = false,
    val gatewayCapable: Boolean = false
)
