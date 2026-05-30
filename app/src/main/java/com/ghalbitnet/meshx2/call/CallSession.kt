package com.ghalbitnet.meshx2.call

data class CallSession(
    val callId: String,
    val localNodeId: String,
    val remoteNodeId: String,
    val remoteGlobalId: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val lastPacketAt: Long = System.currentTimeMillis(),
    val state: CallState = CallState.IDLE,
    val routeHint: String? = null
)

