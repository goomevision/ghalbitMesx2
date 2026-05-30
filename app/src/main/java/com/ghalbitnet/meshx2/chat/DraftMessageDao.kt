package com.ghalbitnet.meshx2.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface DraftMessageDao {
    @Query("SELECT * FROM draft_messages WHERE chat_id = :chatId ORDER BY updated_at DESC LIMIT 1")
    fun findLatestDraft(chatId: String): DraftMessageEntity?

    @Query("SELECT * FROM draft_attachments WHERE draft_id = :draftId LIMIT 1")
    fun findAttachment(draftId: String): DraftAttachmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertDraft(entity: DraftMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAttachment(entity: DraftAttachmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertEdit(entity: MessageEditEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertDelete(entity: MessageDeleteEntity)

    @Query("SELECT COUNT(*) FROM message_edit_events WHERE event_id = :eventId")
    fun countEditEvent(eventId: String): Int

    @Query("SELECT COUNT(*) FROM message_delete_events WHERE event_id = :eventId")
    fun countDeleteEvent(eventId: String): Int

    @Query("DELETE FROM draft_messages WHERE draft_id = :draftId")
    fun deleteDraftMessage(draftId: String)

    @Query("DELETE FROM draft_attachments WHERE draft_id = :draftId")
    fun deleteDraftAttachment(draftId: String)

    @Query("DELETE FROM draft_messages WHERE chat_id = :chatId")
    fun deleteDraftsForChat(chatId: String)

    @Query("DELETE FROM draft_attachments WHERE chat_id = :chatId")
    fun deleteAttachmentsForChat(chatId: String)

    @Query("DELETE FROM draft_messages WHERE updated_at < :cutoff")
    fun cleanupStaleDrafts(cutoff: Long): Int

    @Query("DELETE FROM draft_attachments WHERE draft_id NOT IN (SELECT draft_id FROM draft_messages)")
    fun cleanupOrphanAttachments(): Int

    @Transaction
    fun deleteDraft(draftId: String) {
        deleteDraftAttachment(draftId)
        deleteDraftMessage(draftId)
    }

    @Transaction
    fun replaceDraft(draft: DraftMessageEntity, attachment: DraftAttachmentEntity?) {
        deleteDraftsForChat(draft.chatId)
        deleteAttachmentsForChat(draft.chatId)
        upsertDraft(draft)
        if (attachment != null) {
            upsertAttachment(attachment)
        }
    }
}
