package com.ghalbitnet.meshx2.vpn

data class TcpSessionState(
    val sessionId: String,
    val clientIp: String,
    val clientPort: Int,
    val remoteIp: String,
    val remotePort: Int,
    val clientSeq: Long,
    val clientAck: Long,
    val remoteSeq: Long,
    val remoteAck: Long,
    val windowSize: Int,
    val gatewayId: String,
    val connectionState: TcpConnectionState,
    val lastClientSeq: Long,
    val lastClientAck: Long,
    val lastRemoteSeq: Long,
    val lastRemoteAck: Long,
    val clientWindow: Int,
    val remoteWindow: Int,
    val createdAt: Long,
    val lastSeen: Long,
    val closeReason: String?
)
