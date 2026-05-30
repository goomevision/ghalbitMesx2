package com.ghalbitnet.meshx2.call

import com.ghalbitnet.meshx2.chat.RouteHealthStatus

data class NearbyRouteScore(
    val status: RouteHealthStatus,
    val score: Int,
    val nearbyDetected: Boolean,
    val voiceProbeReady: Boolean,
    val shouldDelayDemotion: Boolean,
    val routeSummary: String
)
