package com.ghalbitnet.meshx2.core.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.ghalbitnet.meshx2.MainActivity
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.chat.ChatDatabase
import com.ghalbitnet.meshx2.chat.ChatMessage
import com.ghalbitnet.meshx2.chat.ConversationIdentityStore
import com.ghalbitnet.meshx2.chat.ChatRetryMetadata
import com.ghalbitnet.meshx2.chat.ChatRetryMetadataRegistry
import com.ghalbitnet.meshx2.chat.ChatSendHelper
import com.ghalbitnet.meshx2.core.log.MeshLogger
import com.ghalbitnet.meshx2.identity.CentralIdentityResolver
import com.ghalbitnet.meshx2.identity.IdentityDisplayFormatter
import com.ghalbitnet.meshx2.online.DeliveryStatus
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
        val peerGlobalId =
            intent.getStringExtra(AppNotificationManager.EXTRA_PEER_GLOBAL_ID)
        val peerPublicKey =
            intent.getStringExtra(AppNotificationManager.EXTRA_PEER_PUBLIC_KEY)
        val peerWalletAddress =
            intent.getStringExtra(AppNotificationManager.EXTRA_PEER_WALLET_ADDRESS)
        val peerDisplayName =
            intent.getStringExtra(AppNotificationManager.EXTRA_PEER_DISPLAY_NAME)

        if (replyText.isBlank() || peerName.isBlank()) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val keyStore =
                KeyStoreManager(context)
            val peerIp =
                keyStore.getPeerAddress(peerName).orEmpty()

            val persistedConversationIdentity =
                ConversationIdentityStore.get(
                    context = context,
                    chatId = peerName
                )

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

            ChatRetryMetadataRegistry.put(
                packetId,
                ChatRetryMetadata(
                    peerGlobalId = peerGlobalId ?: persistedConversationIdentity?.globalId,
                    peerPublicKey = peerPublicKey ?: persistedConversationIdentity?.publicKey,
                    peerWalletAddress = peerWalletAddress ?: persistedConversationIdentity?.walletAddress,
                    peerDisplayName = peerDisplayName ?: persistedConversationIdentity?.canonicalDisplayName
                )
            )

            val ok =
                run {
                    val resolvedIdentity =
                        CentralIdentityResolver.resolve(
                            context = context,
                            legacyChatId = peerName,
                            peerName = peerName,
                            peerIp = peerIp,
                            globalIdHint = peerGlobalId ?: persistedConversationIdentity?.globalId,
                            publicKeyHint = peerPublicKey ?: persistedConversationIdentity?.publicKey ?: keyStore.getPeerKey(peerName),
                            walletAddressHint = peerWalletAddress ?: persistedConversationIdentity?.walletAddress,
                            displayNameHint = peerDisplayName ?: persistedConversationIdentity?.canonicalDisplayName
                        )

                    MeshLogger.i(
                        "RETRY_CHAT_IDENTITY",
                        IdentityDisplayFormatter.secondaryLabel(
                            primaryLabel = resolvedIdentity.primaryLabel,
                            legacyName = peerName,
                            walletAddress = resolvedIdentity.walletAddress,
                            globalId = resolvedIdentity.globalId,
                            publicKey = resolvedIdentity.publicKey,
                            ipAddress = resolvedIdentity.peerIp
                        ) ?: "Unknown peer"
                    )

                ChatSendHelper.sendTextMessage(
                    context = context,
                    keyStore = keyStore,
                    peerName = peerName,
                    peerIp = peerIp,
                    message = replyText,
                    packetId = packetId,
                    peerGlobalId = resolvedIdentity.globalId,
                    peerPublicKey = resolvedIdentity.publicKey,
                    peerWalletAddress = resolvedIdentity.walletAddress,
                    peerDisplayName = resolvedIdentity.displayName
                )
                }

            chatDb.chatDao().updateStatus(
                packetId,
                when (ok.deliveryStatus) {
                    DeliveryStatus.LOCAL_SENT,
                    DeliveryStatus.INTERNET_SENT -> "SENT"
                    DeliveryStatus.ACCEPTED_BY_RELAY -> "ACCEPTED_BY_RELAY"
                    DeliveryStatus.QUEUED_REMOTE -> "QUEUED_REMOTE"
                    DeliveryStatus.MEDIA_UPLOADING -> "MEDIA_UPLOADING"
                    DeliveryStatus.MEDIA_RESUMING -> "MEDIA_RESUMING"
                    DeliveryStatus.MEDIA_QUEUED_REMOTE -> "MEDIA_QUEUED_REMOTE"
                    DeliveryStatus.MEDIA_DELIVERED_REMOTE -> "MEDIA_DELIVERED_REMOTE"
                    DeliveryStatus.MEDIA_READ_REMOTE -> "MEDIA_READ_REMOTE"
                    DeliveryStatus.MEDIA_EXPIRED -> "MEDIA_EXPIRED"
                    DeliveryStatus.DELIVERED_REMOTE -> "DELIVERED_REMOTE"
                    DeliveryStatus.READ_REMOTE -> "READ_REMOTE"
                    DeliveryStatus.EXPIRED_REMOTE -> "EXPIRED_REMOTE"
                    DeliveryStatus.INTERNET_RELAY_NOT_CONFIGURED -> "INTERNET_RELAY_NOT_CONFIGURED"
                    DeliveryStatus.PENDING_SYNC -> "PENDING"
                    DeliveryStatus.FAILED -> "FAILED"
                }
            )

            if (ok.successful) {
                ChatRetryMetadataRegistry.remove(packetId)
            }

            AppNotificationManager.notifyChatMessage(
                context = context,
                peerName = peerName,
                message = if (ok.successful) {
                    if (ok.deliveryStatus == DeliveryStatus.INTERNET_SENT) {
                        context.getString(R.string.chat_sent_online)
                    } else {
                        context.getString(R.string.notification_reply_sent, replyText)
                    }
                } else {
                    if (ok.pendingQueued) {
                        context.getString(R.string.chat_pending_sync)
                    } else {
                        context.getString(R.string.notification_reply_failed)
                    }
                },
                isSilent = true,
                peerGlobalId = peerGlobalId,
                peerPublicKey = peerPublicKey,
                peerWalletAddress = peerWalletAddress,
                peerDisplayName = peerDisplayName
            )
        }
    }
}
