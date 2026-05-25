package com.ghalbitnet.meshx2.vpn

data class UserUsageSession(
    val nodeId: String,
    val sessionId: String,
    val startedAt: Long,
    val lastSeen: Long
)
