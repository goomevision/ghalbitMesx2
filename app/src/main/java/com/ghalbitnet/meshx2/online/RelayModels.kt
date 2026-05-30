package com.ghalbitnet.meshx2.online

data class RelaySendResult(
    val successful: Boolean,
    val status: String,
    val messageId: String,
    val expiresAt: Long = 0L,
    val error: String? = null,
    val responseBody: String? = null
)

data class RelayInboxMessage(
    val messageId: String,
    val packetId: String,
    val senderGlobalId: String,
    val senderNodeId: String,
    val senderPublicKeyHash: String? = null,
    val senderPublicKey: String? = null,
    val senderDisplayName: String? = null,
    val targetGlobalId: String,
    val payload: String,
    val contentType: String,
    val mimeType: String? = null,
    val fileSize: Long = 0L,
    val createdAt: Long,
    val expiresAt: Long
)

data class RelayInboxReceipt(
    val receiptId: String,
    val type: String,
    val messageId: String,
    val packetId: String,
    val senderGlobalId: String,
    val targetGlobalId: String,
    val createdAt: Long
)

data class RelayInboxEdit(
    val eventId: String,
    val originalMessageId: String,
    val packetId: String,
    val senderGlobalId: String,
    val targetGlobalId: String,
    val content: String,
    val editVersion: Int,
    val editedAt: Long
)

data class RelayInboxDelete(
    val eventId: String,
    val originalMessageId: String,
    val packetId: String,
    val senderGlobalId: String,
    val targetGlobalId: String,
    val mode: String,
    val deletedAt: Long
)

data class RelayInboxResult(
    val messages: List<RelayInboxMessage>,
    val receipts: List<RelayInboxReceipt>,
    val edits: List<RelayInboxEdit> = emptyList(),
    val deletes: List<RelayInboxDelete> = emptyList(),
    val error: String? = null
)

data class RemotePresenceResult(
    val online: Boolean,
    val status: String,
    val presence: OnlinePresence? = null,
    val error: String? = null
)

data class RelayMediaInitResult(
    val successful: Boolean,
    val status: String,
    val uploadSessionId: String,
    val mediaId: String,
    val chunkSize: Int,
    val uploadedChunks: Set<Int>,
    val secureMediaToken: String,
    val expiresAt: Long,
    val error: String? = null
)

data class RelayMediaUploadResult(
    val successful: Boolean,
    val status: String,
    val mediaId: String,
    val messageId: String,
    val packetId: String,
    val secureMediaToken: String? = null,
    val expiresAt: Long = 0L,
    val error: String? = null
)

data class RelayRealtimeEvent(
    val type: String,
    val rawJson: String,
    val message: RelayInboxMessage? = null,
    val receipt: RelayInboxReceipt? = null,
    val presence: OnlinePresence? = null,
    val edit: RelayInboxEdit? = null,
    val delete: RelayInboxDelete? = null
)
