package com.ghalbitnet.meshx2.identity

data class RoutingShadowIdentity(
    val routeOwnerLegacyId: String,
    val routeOwnerGlobalId: String?,
    val routeOwnerPublicKey: String?,
    val confidence: Int,
    val source: String,
    val riskLevel: String,
    val lastSeenAt: Long,
    val walletAddress: String? = null,
    val peerIp: String? = null,
    val peerName: String? = null
)
