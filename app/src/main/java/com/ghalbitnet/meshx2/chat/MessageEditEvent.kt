package com.ghalbitnet.meshx2.chat

data class MessageEditEvent(
    val eventId: String,
    val packetId: String,
    val chatId: String,
    val content: String,
    val editVersion: Int,
    val createdAt: Long,
    val senderGlobalId: String? = null,
    val delivered: Boolean = false
)
