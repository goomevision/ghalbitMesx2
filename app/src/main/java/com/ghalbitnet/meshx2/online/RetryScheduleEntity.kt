package com.ghalbitnet.meshx2.online

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "retry_schedule",
    indices = [
        Index("next_retry_at"),
        Index("category"),
        Index("expires_at")
    ]
)
data class RetryScheduleEntity(
    @PrimaryKey
    @ColumnInfo(name = "packet_id")
    val packetId: String,
    @ColumnInfo(name = "retry_attempt")
    val retryAttempt: Int,
    @ColumnInfo(name = "last_attempt_at")
    val lastAttemptAt: Long,
    @ColumnInfo(name = "next_retry_at")
    val nextRetryAt: Long,
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long,
    @ColumnInfo(name = "category")
    val category: String,
    @ColumnInfo(name = "priority")
    val priority: Int
)
