package com.ghalbitnet.meshx2.chat
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    indices = [
        Index(
            value = ["packetId"],
            unique = true
        )
    ]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packetId: String = "",
    val chatId: String,
    val senderName: String,
    val content: String,
    val contentType: String = "TEXT",
    val messageType: String = MessageVisibility.USER_VISIBLE_MESSAGE.name,
    val visibilityType: String = MessageVisibility.VISIBLE.name,
    val internalSignalType: String? = null,
    val filePath: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isSent: Boolean,
    val status: String = "RECEIVED"
)
