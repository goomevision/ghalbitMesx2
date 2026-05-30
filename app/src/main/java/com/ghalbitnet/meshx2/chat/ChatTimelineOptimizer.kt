package com.ghalbitnet.meshx2.chat

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.settings.DeveloperModeManager

object ChatTimelineOptimizer {
    private const val TRANSIENT_WINDOW_MS = 5_000L
    private const val MERGE_WINDOW_MS = 6_000L

    fun optimize(context: Context, input: List<ChatMessage>): List<ChatMessage> {
        val developerMode = DeveloperModeManager.isEnabled(context)
        val now = System.currentTimeMillis()
        val visible = mutableListOf<ChatMessage>()
        var lastRouteEvent: ChatMessage? = null
        var routeMergeCount = 0
        input.forEach { message ->
            val style = SystemEventStyle.fromMessage(message)
            val signal = message.internalSignalType.orEmpty().uppercase()
            if (!developerMode && (signal == "HEARTBEAT_SIGNAL" || signal == "VOICE_ACK")) {
                return@forEach
            }
            if (!developerMode && style.autoHide && now - message.timestamp > TRANSIENT_WINDOW_MS) {
                return@forEach
            }
            if (!developerMode && style.category == "ROUTE_EVENT") {
                val previous = lastRouteEvent
                if (previous != null && message.timestamp - previous.timestamp <= MERGE_WINDOW_MS) {
                    routeMergeCount += 1
                    val merged =
                        previous.copy(
                            content = "Rute komunikasi diperbarui beberapa kali",
                            timestamp = message.timestamp
                        )
                    visible[visible.lastIndex] = merged
                    lastRouteEvent = merged
                    return@forEach
                }
                routeMergeCount = 1
                lastRouteEvent = message
            }
            if (!developerMode && style.category == "RELAY_EVENT") {
                val existingIndex = visible.indexOfLast { SystemEventStyle.fromMessage(it).category == "RELAY_EVENT" }
                if (existingIndex >= 0) {
                    visible.removeAt(existingIndex)
                }
            }
            visible += message
        }
        if (routeMergeCount > 1) {
            Log.d("GHALBIT-CHAT-TIMELINE", "route events merged")
        }
        return visible
    }
}
