package com.ghalbitnet.meshx2.chat

data class MessageDeleteEvent(
    val eventId: String,
    val packetId: String,
    val chatId: String,
    val mode: String,
    val createdAt: Long,
    val senderGlobalId: String? = null,
    val delivered: Boolean = false
)
