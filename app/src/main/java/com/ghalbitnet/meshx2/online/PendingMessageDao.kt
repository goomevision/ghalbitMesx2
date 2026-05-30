package com.ghalbitnet.meshx2.online

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface PendingMessageDao {
    @Query("SELECT * FROM pending_messages ORDER BY created_at DESC")
    fun allMessages(): List<PendingMessageEntity>

    @Query("SELECT * FROM pending_messages WHERE packet_id = :packetId LIMIT 1")
    fun findMessage(packetId: String): PendingMessageEntity?

    @Query("SELECT COUNT(*) FROM pending_messages WHERE chat_id = :chatId")
    fun countForChat(chatId: String): Int

    @Query("SELECT * FROM pending_media WHERE packet_id = :packetId LIMIT 1")
    fun findMedia(packetId: String): PendingMediaEntity?

    @Query("SELECT * FROM pending_media WHERE media_uri IS NOT NULL AND media_uri != ''")
    fun allMedia(): List<PendingMediaEntity>

    @Query("SELECT * FROM pending_receipts WHERE delivered = 0 ORDER BY created_at ASC")
    fun allPendingReceipts(): List<PendingReceiptEntity>

    @Query("SELECT * FROM retry_schedule ORDER BY next_retry_at ASC")
    fun allRetrySchedules(): List<RetryScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertMessage(entity: PendingMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertMedia(entity: PendingMediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertReceipt(entity: PendingReceiptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertRetrySchedule(entity: RetryScheduleEntity)

    @Query("DELETE FROM pending_messages WHERE packet_id = :packetId")
    fun deleteMessage(packetId: String)

    @Query("DELETE FROM pending_media WHERE packet_id = :packetId")
    fun deleteMedia(packetId: String)

    @Query("DELETE FROM retry_schedule WHERE packet_id = :packetId")
    fun deleteRetrySchedule(packetId: String)

    @Query("DELETE FROM pending_receipts WHERE receipt_id = :receiptId")
    fun deleteReceipt(receiptId: String)

    @Query("DELETE FROM pending_messages WHERE expires_at > 0 AND expires_at <= :now")
    fun deleteExpiredMessages(now: Long): Int

    @Query("DELETE FROM pending_media WHERE packet_id NOT IN (SELECT packet_id FROM pending_messages)")
    fun cleanupOrphanMedia(): Int

    @Query("DELETE FROM retry_schedule WHERE packet_id NOT IN (SELECT packet_id FROM pending_messages)")
    fun cleanupOrphanSchedules(): Int

    @Transaction
    fun deletePending(packetId: String) {
        deleteMessage(packetId)
        deleteMedia(packetId)
        deleteRetrySchedule(packetId)
    }
}
