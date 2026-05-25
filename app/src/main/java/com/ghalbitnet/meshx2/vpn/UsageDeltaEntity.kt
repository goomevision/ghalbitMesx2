package com.ghalbitnet.meshx2.vpn

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "usage_deltas",
    indices = [Index(value = ["sessionId"])]
)
data class UsageDeltaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: String,
    val timestamp: Long,
    val uploadDelta: Long,
    val downloadDelta: Long,
    val totalDelta: Long,
    val source: String,
    val synced: Boolean = false
)
