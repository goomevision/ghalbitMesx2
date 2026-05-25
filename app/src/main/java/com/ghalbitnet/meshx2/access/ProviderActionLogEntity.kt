package com.ghalbitnet.meshx2.access

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "provider_action_logs")
data class ProviderActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String,
    val clientIp: String,
    val nodeId: String? = null,
    val note: String,
    val providerId: String
)
