package com.ghalbitnet.meshx2.routing

import com.ghalbitnet.meshx2.call.CallPeerEndpoint

data class ValidatedRouteCandidate(
    val routeType: String,
    val endpoint: CallPeerEndpoint,
    val finalScore: Int,
    val humanStatus: String,
    val probeResult: RouteProbeResult? = null,
    val staleHint: Boolean = false
)
