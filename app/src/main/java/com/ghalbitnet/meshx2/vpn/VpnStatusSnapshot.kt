package com.ghalbitnet.meshx2.vpn

data class VpnStatusSnapshot(
    val desiredRunning: Boolean,
    val serviceActive: Boolean,
    val runtimeAvailable: Boolean,
    val runtimeAgeMs: Long?,
    val runtimeFreshness: RuntimeFreshness,
    val mode: String?,
    val gatewayName: String?,
    val connectedUsers: Int?,
    val packetsIn: Long?,
    val packetsOut: Long?,
    val lastDecision: String?,
    val lastUpdatedAt: Long,
    val uiStatus: String,
    val warning: String?
)
