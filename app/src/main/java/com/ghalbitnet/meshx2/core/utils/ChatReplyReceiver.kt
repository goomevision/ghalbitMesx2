package com.ghalbitnet.meshx2.core.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.ghalbitnet.meshx2.MainActivity
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.chat.ChatDatabase
import com.ghalbitnet.meshx2.chat.ChatMessage
import com.ghalbitnet.meshx2.chat.ChatSendHelper
import com.ghalbitnet.meshx2.security.KeyStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatReplyReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val replyText =
            RemoteInput.getResultsFromIntent(intent)
                ?.getCharSequence(AppNotificationManager.KEY_TEXT_REPLY)
                ?.toString()
                ?.trim()
                .orEmpty()

        val peerName =
            intent.getStringExtra(AppNotificationManager.EXTRA_PEER_NAME)
                .orEmpty()

        if (replyText.isBlank() || peerName.isBlank()) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val keyStore =
                KeyStoreManager(context)
            val peerIp =
                keyStore.getPeerAddress(peerName).orEmpty()

            if (peerIp.isBlank()) {
                return@launch
            }

            val packetId =
                "CHAT-" + System.currentTimeMillis()
            val chatDb =
                ChatDatabase.getInstance(context)

            chatDb.chatDao().insertMessage(
                ChatMessage(
                    packetId = packetId,
                    chatId = peerName,
                    senderName = "ME",
                    content = replyText,
                    isSent = true,
                    status = "SENDING"
                )
            )

            val ok =
                ChatSendHelper.sendTextMessage(
                    keyStore = keyStore,
                    peerName = peerName,
                    peerIp = peerIp,
                    message = replyText,
                    packetId = packetId
                )

            chatDb.chatDao().updateStatus(
                packetId,
                if (ok) "SENT" else "FAILED"
            )

            AppNotificationManager.notifyChatMessage(
                context = context,
                peerName = peerName,
                message = if (ok) {
                    context.getString(R.string.notification_reply_sent, replyText)
                } else {
                    context.getString(R.string.notification_reply_failed)
                },
                isSilent = true
            )
        }
    }
}
