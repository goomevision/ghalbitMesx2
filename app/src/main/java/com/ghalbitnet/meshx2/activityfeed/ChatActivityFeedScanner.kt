package com.ghalbitnet.meshx2.activityfeed

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.chat.ChatDatabase
import com.ghalbitnet.meshx2.chat.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object ChatActivityFeedScanner {
    private const val TAG = "GHALBIT-ACTIVITY-CHAT"
    private const val SCAN_INTERVAL_MS = 8_000L
    private const val MAX_MESSAGES_PER_CHAT = 80
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val lastKnownStatus = ConcurrentHashMap<String, String>()

    fun start(context: Context) {
        val appContext = context.applicationContext
        ActivityFeedManager.bind(appContext)
        if (!started.compareAndSet(false, true)) return

        ActivityFeedManager.publish(
            type = ActivityFeedType.RUNTIME_EVENT,
            title = "Chat feed scanner aktif",
            message = "Activity Feed mulai memantau status pesan chat.",
            source = "ChatActivityFeedScanner",
            metadata = "{\"intervalMs\":$SCAN_INTERVAL_MS}"
        )

        scope.launch {
            while (true) {
                runCatching {
                    scan(appContext)
                }.onFailure {
                    Log.e(TAG, "scan failed", it)
                    ActivityFeedManager.publish(
                        type = ActivityFeedType.SYNC_FAILED,
                        title = "Chat feed scanner error",
                        message = it.message ?: "Gagal memindai status chat.",
                        source = "ChatActivityFeedScanner"
                    )
                }
                delay(SCAN_INTERVAL_MS)
            }
        }
    }

    private fun scan(context: Context) {
        val dao = ChatDatabase.getInstance(context).chatDao()
        dao.getChatIds().forEach { chatId ->
            dao.getMessages(chatId)
                .takeLast(MAX_MESSAGES_PER_CHAT)
                .forEach { message ->
                    val previous = lastKnownStatus.put(message.packetId, message.status)
                    if (previous == null) {
                        publishFirstSeen(message)
                    } else if (previous != message.status) {
                        ChatActivityFeedBridge.stateChanged(
                            chatId = message.chatId,
                            packetId = message.packetId,
                            state = message.status,
                            reason = "dbStatusChanged"
                        )
                    }
                }
        }
    }

    private fun publishFirstSeen(message: ChatMessage) {
        if (message.isSent) {
            ChatActivityFeedBridge.stateChanged(
                chatId = message.chatId,
                packetId = message.packetId,
                state = message.status,
                reason = "firstSeenOutgoing"
            )
        } else {
            ChatActivityFeedBridge.incomingReceived(
                chatId = message.chatId,
                messageId = message.packetId,
                senderName = message.senderName,
                contentType = message.contentType
            )
        }
    }
}
