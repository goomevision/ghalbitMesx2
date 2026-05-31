package com.ghalbitnet.meshx2.activityfeed

object ChatActivityFeedBridge {

    fun outgoingQueued(
        chatId: String,
        messageId: String,
        peerDisplayName: String? = null
    ) {
        ActivityFeedManager.publish(
            type = ActivityFeedType.CHAT_SENT,
            title = "Pesan masuk antrean",
            message = "Pesan ke ${peerDisplayName ?: chatId} disiapkan untuk dikirim.",
            peerId = chatId,
            source = "ChatDeliveryManager",
            metadata = "{\"messageId\":\"$messageId\",\"state\":\"QUEUED\"}"
        )
    }

    fun stateChanged(
        chatId: String? = null,
        packetId: String,
        state: String,
        reason: String? = null
    ) {
        val type = when {
            state.contains("READ", ignoreCase = true) -> ActivityFeedType.CHAT_RECEIVED
            state.contains("DELIVERED", ignoreCase = true) -> ActivityFeedType.CHAT_SENT
            state.contains("FAILED", ignoreCase = true) -> ActivityFeedType.SYNC_FAILED
            state.contains("WAITING", ignoreCase = true) -> ActivityFeedType.RUNTIME_EVENT
            state.contains("PENDING", ignoreCase = true) -> ActivityFeedType.RUNTIME_EVENT
            else -> ActivityFeedType.CHAT_SENT
        }

        val title = when {
            state.contains("READ", ignoreCase = true) -> "Pesan dibaca"
            state.contains("DELIVERED", ignoreCase = true) -> "Pesan terkirim"
            state.contains("FAILED_FINAL", ignoreCase = true) -> "Pesan gagal final"
            state.contains("FAILED", ignoreCase = true) -> "Pesan menunggu retry"
            state.contains("WAITING", ignoreCase = true) -> "Pesan menunggu jalur"
            state.contains("PENDING", ignoreCase = true) -> "Pesan pending"
            state.contains("SENDING", ignoreCase = true) -> "Pesan sedang dikirim"
            else -> "Status pesan berubah"
        }

        ActivityFeedManager.publish(
            type = type,
            title = title,
            message = "Status pesan $packetId berubah menjadi $state${reason?.let { ": $it" } ?: ""}.",
            peerId = chatId,
            source = "ChatDeliveryManager",
            metadata = "{\"packetId\":\"$packetId\",\"state\":\"$state\",\"reason\":\"${reason ?: ""}\"}"
        )
    }

    fun incomingReceived(
        chatId: String,
        messageId: String,
        senderName: String? = null,
        contentType: String? = null
    ) {
        ActivityFeedManager.publish(
            type = ActivityFeedType.CHAT_RECEIVED,
            title = "Pesan diterima",
            message = "Pesan baru dari ${senderName ?: chatId}.",
            peerId = chatId,
            source = "ChatDeliveryManager",
            metadata = "{\"messageId\":\"$messageId\",\"contentType\":\"${contentType ?: "TEXT"}\"}"
        )
    }

    fun retryScheduled(
        chatId: String? = null,
        messageId: String,
        attempt: Int,
        nextRetryAt: Long,
        reason: String
    ) {
        ActivityFeedManager.publish(
            type = ActivityFeedType.RUNTIME_EVENT,
            title = "Retry pesan dijadwalkan",
            message = "Pesan $messageId akan dicoba lagi. Attempt=$attempt, reason=$reason.",
            peerId = chatId,
            source = "ChatDeliveryManager",
            metadata = "{\"messageId\":\"$messageId\",\"attempt\":$attempt,\"nextRetryAt\":$nextRetryAt,\"reason\":\"$reason\"}"
        )
    }

    fun relayAccepted(
        chatId: String? = null,
        messageId: String,
        status: String
    ) {
        ActivityFeedManager.publish(
            type = ActivityFeedType.SYNC_SUCCESS,
            title = "Pesan diterima relay",
            message = "Relay menerima pesan $messageId dengan status $status.",
            peerId = chatId,
            source = "ChatDeliveryManager",
            metadata = "{\"messageId\":\"$messageId\",\"relayStatus\":\"$status\"}"
        )
    }
}
