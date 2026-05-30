package com.ghalbitnet.meshx2.routing

data class RouteHint(
    val destinationId: String,
    val nextHopId: String,
    val latencyMs: Long,
    val hopCount: Int,
    val trustScore: Int,
    val lastSeen: Long = System.currentTimeMillis()
)
