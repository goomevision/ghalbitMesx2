package com.ghalbitnet.meshx2.chat

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "message_edit_events",
    indices = [
        Index("packet_id"),
        Index("chat_id")
    ]
)
data class MessageEditEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id")
    val eventId: String,
    @ColumnInfo(name = "packet_id")
    val packetId: String,
    @ColumnInfo(name = "chat_id")
    val chatId: String,
    @ColumnInfo(name = "content")
    val content: String,
    @ColumnInfo(name = "edit_version")
    val editVersion: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "sender_global_id")
    val senderGlobalId: String?,
    @ColumnInfo(name = "delivered")
    val delivered: Boolean
)
