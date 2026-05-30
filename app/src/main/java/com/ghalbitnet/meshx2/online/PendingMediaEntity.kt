package com.ghalbitnet.meshx2.online

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_media",
    indices = [
        Index("remote_media_id")
    ]
)
data class PendingMediaEntity(
    @PrimaryKey
    @ColumnInfo(name = "packet_id")
    val packetId: String,
    @ColumnInfo(name = "media_uri")
    val mediaUri: String?,
    @ColumnInfo(name = "media_type")
    val mediaType: String?,
    @ColumnInfo(name = "mime_type")
    val mimeType: String?,
    @ColumnInfo(name = "file_size")
    val fileSize: Long,
    @ColumnInfo(name = "media_checksum")
    val mediaChecksum: String?,
    @ColumnInfo(name = "chunk_count")
    val chunkCount: Int,
    @ColumnInfo(name = "uploaded_chunks")
    val uploadedChunks: String,
    @ColumnInfo(name = "upload_session_id")
    val uploadSessionId: String?,
    @ColumnInfo(name = "remote_media_id")
    val remoteMediaId: String?,
    @ColumnInfo(name = "secure_media_token")
    val secureMediaToken: String?,
    @ColumnInfo(name = "upload_state")
    val uploadState: String?,
    @ColumnInfo(name = "last_progress_at")
    val lastProgressAt: Long
)
