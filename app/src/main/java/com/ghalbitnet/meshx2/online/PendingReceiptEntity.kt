package com.ghalbitnet.meshx2.online

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_receipts",
    indices = [
        Index("target_global_id"),
        Index("next_retry_at")
    ]
)
data class PendingReceiptEntity(
    @PrimaryKey
    @ColumnInfo(name = "receipt_id")
    val receiptId: String,
    @ColumnInfo(name = "packet_id")
    val packetId: String,
    @ColumnInfo(name = "message_id")
    val messageId: String,
    @ColumnInfo(name = "target_global_id")
    val targetGlobalId: String,
    @ColumnInfo(name = "receipt_type")
    val receiptType: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "next_retry_at")
    val nextRetryAt: Long,
    @ColumnInfo(name = "delivered")
    val delivered: Boolean
)
