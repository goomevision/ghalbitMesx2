package com.ghalbitnet.meshx2.call

data class VoipTarget(
    val callId: String,
    val nodeId: String,
    val globalId: String?,
    val publicKeyHash: String?,
    val displayName: String,
    val routeType: VoipRouteType,
    val routeHint: String?,
    val incoming: Boolean = false
)
