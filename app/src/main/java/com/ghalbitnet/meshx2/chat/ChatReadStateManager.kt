package com.ghalbitnet.meshx2.chat

import android.content.Context

object ChatReadStateManager {
    private const val PREFS_NAME = "chat_read_state"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun markChatViewed(
        context: Context,
        chatId: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        prefs(context).edit().putLong(chatId, timestamp).apply()
    }

    fun getLastViewedAt(
        context: Context,
        chatId: String
    ): Long = prefs(context).getLong(chatId, 0L)

    fun unreadCount(
        context: Context,
        chatId: String,
        messages: List<ChatMessage>
    ): Int {
        val lastViewedAt = getLastViewedAt(context, chatId)
        return messages.count { !it.isSent && it.timestamp > lastViewedAt }
    }
}
