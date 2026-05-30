package com.ghalbitnet.meshx2.chat
import androidx.room.*

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId AND visibilityType != 'HIDDEN' ORDER BY timestamp ASC")
    fun getMessages(chatId: String): List<ChatMessage>

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId AND isSent = 1 AND status IN ('FAILED','FAILED_RETRYING','FAILED_FINAL','PENDING','WAITING_FOR_PEER') ORDER BY timestamp DESC LIMIT 1")
    fun getLastFailedMessage(chatId: String): ChatMessage?

    @Query("SELECT DISTINCT chatId FROM chat_messages WHERE visibilityType != 'HIDDEN' ORDER BY chatId ASC")
    fun getChatIds(): List<String>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE packetId = :packetId")
    fun countByPacketId(packetId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertMessage(message: ChatMessage): Long

    @Query("UPDATE chat_messages SET status = :status WHERE packetId = :packetId")
    fun updateStatus(packetId: String, status: String)

    @Query("UPDATE chat_messages SET content = :content WHERE packetId = :packetId")
    fun updateContent(packetId: String, content: String)

    @Query("UPDATE chat_messages SET content = :content, status = :status WHERE packetId = :packetId")
    fun updateContentAndStatus(packetId: String, content: String, status: String)

    @Query("UPDATE chat_messages SET content = :content, filePath = :filePath, status = :status WHERE packetId = :packetId")
    fun updateAttachmentAndStatus(packetId: String, content: String, filePath: String?, status: String)

    @Query("SELECT * FROM chat_messages WHERE packetId = :packetId LIMIT 1")
    fun findByPacketId(packetId: String): ChatMessage?

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId AND isSent = 0 AND visibilityType != 'HIDDEN' AND status NOT IN ('READ','READ_REMOTE') ORDER BY timestamp DESC LIMIT 25")
    fun getUnreadIncoming(chatId: String): List<ChatMessage>
}
