package com.ghalbitnet.meshx2.vpn

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage_sessions")
data class UsageSessionEntity(
    @PrimaryKey val sessionId: String,
    val nodeId: String,
    val startTime: Long,
    val endTime: Long? = null,
    val totalUploadBytes: Long = 0L,
    val totalDownloadBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val packetCount: Long = 0L,
    val tcpCount: Long = 0L,
    val udpCount: Long = 0L,
    val icmpCount: Long = 0L,
    val ipv6Count: Long = 0L,
    val unknownCount: Long = 0L,
    val operatingMode: String,
    val providerNodeId: String? = null,
    val gatewayNodeId: String? = null,
    val isSynced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
