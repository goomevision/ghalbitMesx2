package com.ghalbitnet.meshx2.online

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_messages",
    indices = [
        Index("chat_id"),
        Index("target_global_id"),
        Index("expires_at")
    ]
)
data class PendingMessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "packet_id")
    val packetId: String,
    @ColumnInfo(name = "message_id")
    val messageId: String,
    @ColumnInfo(name = "chat_id")
    val chatId: String,
    @ColumnInfo(name = "target_node_id")
    val targetNodeId: String,
    @ColumnInfo(name = "target_global_id")
    val targetGlobalId: String?,
    @ColumnInfo(name = "content")
    val content: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long,
    @ColumnInfo(name = "delivery_status")
    val deliveryStatus: String,
    @ColumnInfo(name = "route_hint")
    val routeHint: String?,
    @ColumnInfo(name = "peer_public_key")
    val peerPublicKey: String?,
    @ColumnInfo(name = "peer_wallet_address")
    val peerWalletAddress: String?,
    @ColumnInfo(name = "peer_display_name")
    val peerDisplayName: String?,
    @ColumnInfo(name = "last_failure_reason")
    val lastFailureReason: String?
)
