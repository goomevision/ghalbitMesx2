package com.ghalbitnet.meshx2.chat

data class DraftAttachment(
    val draftId: String,
    val contentType: String,
    val filePath: String,
    val displayName: String,
    val mimeType: String,
    val fileSize: Long,
    val warning: String? = null
)
