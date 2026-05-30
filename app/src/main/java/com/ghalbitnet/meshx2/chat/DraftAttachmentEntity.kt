package com.ghalbitnet.meshx2.chat

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "draft_attachments",
    indices = [Index("chat_id")]
)
data class DraftAttachmentEntity(
    @PrimaryKey
    @ColumnInfo(name = "draft_id")
    val draftId: String,
    @ColumnInfo(name = "chat_id")
    val chatId: String,
    @ColumnInfo(name = "content_type")
    val contentType: String,
    @ColumnInfo(name = "file_path")
    val filePath: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    @ColumnInfo(name = "file_size")
    val fileSize: Long,
    @ColumnInfo(name = "warning")
    val warning: String?
)
