package com.ghalbitnet.meshx2.chat

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "draft_messages",
    indices = [
        Index("chat_id"),
        Index("updated_at")
    ]
)
data class DraftMessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "draft_id")
    val draftId: String,
    @ColumnInfo(name = "chat_id")
    val chatId: String,
    @ColumnInfo(name = "draft_type")
    val draftType: String,
    @ColumnInfo(name = "content")
    val content: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
