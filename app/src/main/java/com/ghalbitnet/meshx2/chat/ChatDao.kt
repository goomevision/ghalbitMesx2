package com.ghalbitnet.meshx2.chat
import androidx.room.*

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessages(chatId: String): List<ChatMessage>

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId AND isSent = 1 AND status = 'FAILED' ORDER BY timestamp DESC LIMIT 1")
    fun getLastFailedMessage(chatId: String): ChatMessage?

    @Query("SELECT DISTINCT chatId FROM chat_messages ORDER BY chatId ASC")
    fun getChatIds(): List<String>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE packetId = :packetId")
    fun countByPacketId(packetId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertMessage(message: ChatMessage): Long

    @Query("UPDATE chat_messages SET status = :status WHERE packetId = :packetId")
    fun updateStatus(packetId: String, status: String)
}
