package com.ghalbitnet.meshx2.vpn

data class UsageCounter(
    val nodeId: String,
    val sessionId: String,
    val totalPackets: Long = 0L,
    val totalUploadBytes: Long = 0L,
    val totalDownloadBytes: Long = 0L,
    val tcpPackets: Long = 0L,
    val udpPackets: Long = 0L,
    val icmpPackets: Long = 0L,
    val ipv6Packets: Long = 0L,
    val unknownPackets: Long = 0L,
    val updatedAt: Long = 0L
)
