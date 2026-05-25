package com.ghalbitnet.meshx2.access

data class UnauthorizedClientUiModel(
    val ipAddress: String,
    val macAddress: String?,
    val deviceName: String?,
    val authStatus: NetworkAccessPolicy.AuthStatus,
    val trustLevel: ClientTrustLevel,
    val accessTokenStatus: String,
    val firstSeen: Long,
    val lastSeen: Long,
    val reason: String,
    val detail: String,
    val detectedAt: Long,
    val nodeId: String?,
    val manuallyAllowed: Boolean
)
