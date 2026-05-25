package com.ghalbitnet.meshx2.vpn

data class PacketFlowSnapshot(
    val flowId: String,
    val sessionId: String,
    val packetId: String,
    val protocolName: String,
    val ipVersion: Int,
    val packetLength: Int,
    val routeMode: String,
    val sourceNodeId: String,
    val gatewayNodeId: String,
    val timestamp: Long,
    val parseStatus: String,
    val sourceIp: String? = null,
    val sourcePort: Int? = null,
    val destinationIp: String? = null,
    val destinationPort: Int? = null,
    val tcpState: String? = null,
    val decision: String = ""
)

typealias TcpFlowDebugSnapshot = PacketFlowSnapshot
