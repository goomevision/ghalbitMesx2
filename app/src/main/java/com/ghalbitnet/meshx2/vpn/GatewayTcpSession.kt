package com.ghalbitnet.meshx2.vpn

import java.net.Socket

data class GatewayTcpSession(
    val clientNodeId: String,
    val sessionId: String,
    val gatewayNodeId: String,
    @Volatile var packetId: String,
    val sourceAddress: String,
    val sourcePort: Int,
    val destinationAddress: String,
    val destinationPort: Int,
    val remoteHost: String,
    val socket: Socket,
    val createdAt: Long,
    @Volatile var lastSeen: Long
) {
    val key: String
        get() = "$clientNodeId|$sessionId|$sourceAddress|$sourcePort|$destinationAddress|$destinationPort"
}
