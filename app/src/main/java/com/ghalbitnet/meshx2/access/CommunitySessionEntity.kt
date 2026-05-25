package com.ghalbitnet.meshx2.access

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "community_sessions")
data class CommunitySessionEntity(
    @PrimaryKey val clientId: String,
    val nodeId: String? = null,
    val ipAddress: String,
    val macAddress: String? = null,
    val trustLevel: String,
    val authStatus: String,
    val accessTokenStatus: String,
    val firstSeen: Long,
    val lastSeen: Long,
    val uploadBytes: Long = 0L,
    val downloadBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isManualApproved: Boolean = false,
    val isBlocked: Boolean = false,
    val isSuspicious: Boolean = false,
    val providerNote: String? = null
)
