package com.ghalbitnet.meshx2.chat

data class ActiveConversationRouteState(
    val chatId: String,
    val globalId: String? = null,
    val lastPingAt: Long = 0L,
    val lastPongAt: Long = 0L,
    val latencyMs: Long = -1L,
    val rollingAverageLatencyMs: Long = -1L,
    val packetLossEstimate: Int = 0,
    val routeStabilityScore: Int = 0,
    val reconnectCounter: Int = 0,
    val transport: String = "-",
    val activeRoute: String = "-",
    val routeHealth: RouteHealthStatus = RouteHealthStatus.RECONNECTING
)
