package com.ghalbitnet.meshx2.call

data class CallPeerEndpoint(
    val nodeId: String,
    val globalId: String? = null,
    val publicKey: String? = null,
    val publicKeyHash: String? = null,
    val walletAddress: String? = null,
    val displayName: String? = null,
    val routeHint: String? = null,
    val transportIp: String? = null
)
