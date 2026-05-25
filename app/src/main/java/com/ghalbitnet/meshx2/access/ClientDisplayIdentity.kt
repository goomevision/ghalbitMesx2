package com.ghalbitnet.meshx2.access

data class ClientDisplayIdentity(
    val displayNumber: Int,
    val displayName: String?,
    val ipAddress: String,
    val macAddress: String?,
    val shortMac: String?,
    val authStatus: NetworkAccessPolicy.AuthStatus,
    val trustLevel: ClientTrustLevel,
    val lastSeen: Long
)
