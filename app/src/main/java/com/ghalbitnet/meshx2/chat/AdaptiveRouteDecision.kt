package com.ghalbitnet.meshx2.chat

data class AdaptiveRouteDecision(
    val chatId: String,
    val globalId: String? = null,
    val routeType: AdaptiveRouteType,
    val transport: String,
    val nextHop: String? = null,
    val reason: String,
    val routeHealth: RouteHealthStatus
)
