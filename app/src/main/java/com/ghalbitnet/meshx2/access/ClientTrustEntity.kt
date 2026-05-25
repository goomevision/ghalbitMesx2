package com.ghalbitnet.meshx2.access

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "client_trust")
data class ClientTrustEntity(
    @PrimaryKey val clientIp: String,
    val nodeId: String? = null,
    val trustLevel: String = ClientTrustLevel.UNKNOWN.name,
    val trustScore: Int = 0,
    val isManualApproved: Boolean = false,
    val isSuspicious: Boolean = false,
    val isBlocked: Boolean = false,
    val reconnectCount: Int = 0,
    val providerNote: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
