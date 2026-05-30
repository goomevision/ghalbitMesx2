package com.ghalbitnet.meshx2.routing

data class RouteProbeResult(
    val success: Boolean,
    val host: String? = null,
    val port: Int = 56565,
    val reason: String,
    val latencyMs: Long = -1L,
    val aliveNodes: Int = 0,
    val matchedAliveNode: Boolean = false,
    val staleHint: Boolean = false
)
