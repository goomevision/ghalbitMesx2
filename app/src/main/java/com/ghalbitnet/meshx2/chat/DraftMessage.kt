package com.ghalbitnet.meshx2.chat

data class DraftMessage(
    val draftId: String,
    val chatId: String,
    val draftType: String,
    val content: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val attachment: DraftAttachment? = null
)
