package com.ghalbitnet.meshx2.network

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.call.CallSessionActivity
import com.ghalbitnet.meshx2.call.VoiceCallRegistry
import com.ghalbitnet.meshx2.chat.ChatActivity
import com.ghalbitnet.meshx2.chat.ChatDatabase
import com.ghalbitnet.meshx2.chat.ChatMessage
import com.ghalbitnet.meshx2.chat.MessagingReceiver
import com.ghalbitnet.meshx2.core.utils.AppNotificationManager
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.model.SecurePacket
import com.ghalbitnet.meshx2.routing.PacketTtlManager
import com.ghalbitnet.meshx2.security.CryptoEngine
import com.ghalbitnet.meshx2.security.KeyStoreManager
import com.ghalbitnet.meshx2.stats.MeshStatistics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class IncomingPacketHandler(
    private val context: Context,
    private val appContext: Context,
    private val keyStore: KeyStoreManager,
    private val chatDb: ChatDatabase,
    private val localPeerId: String,
    private val scope: CoroutineScope,
    private val listener: IncomingPacketListener,
    private val openIncomingCall: (Intent) -> Unit
) {

    interface IncomingPacketListener {
        fun onChatReceived(summary: String)
        fun onSosReceived(summary: String)
        fun onCallInviteReceived(summary: String)
        fun onPacketStatus(message: String)
        fun onPacketError(message: String, throwable: Throwable? = null)
    }

    fun handleSecurePacket(secure: SecurePacket) {
        try {
            MessagingReceiver.onSecurePacket(
                secure,
                keyStore,
                chatDb
            )
        } catch (error: Exception) {
            Log.e("GHALBIT", "Secure packet error", error)
            listener.onPacketError("Secure packet error", error)
        }
    }

    fun processIncomingPacket(packet: MeshPacket) {
        try {
            val payload =
                if (packet.encrypted) {
                    decryptPayload(packet)
                } else {
                    packet.payload
                }

            MeshStatistics.receivedPacket(packet.type, packet.source)

            if (packet.type == "ACK") {
                AckTracker.markAckReceived(packet.payload)
            }

            listener.onPacketStatus("PACKET ${packet.type}")
            listener.onPacketStatus("FROM ${packet.source}")
            listener.onPacketStatus("MSG $payload")

            val intent =
                Intent("com.ghalbitnet.meshx2.NEW_MESH_PACKET").apply {
                    putExtra("packetId", packet.packetId)
                    putExtra("source", packet.source)
                    putExtra("destination", packet.destination)
                    putExtra("payload", payload)
                    putExtra("type", packet.type)
                    putExtra("encrypted", packet.encrypted)
                }

            LocalBroadcastManager
                .getInstance(context)
                .sendBroadcast(intent)

            when (packet.type) {
                "CHAT" -> {
                    handleIncomingChatMessage(packet, payload)
                    sendAck(packet)
                }
                "SOS" -> handleIncomingSos(packet, payload)
                "CALL_INVITE" -> handleIncomingCallInvite(packet, payload)
            }
        } catch (error: Exception) {
            Log.e("GHALBIT", "Packet error", error)
            listener.onPacketError("Packet error", error)
        }
    }

    private fun handleIncomingCallInvite(
        packet: MeshPacket,
        payload: String
    ) {
        val callId =
            try {
                JSONObject(payload).optString("callId")
            } catch (_: Exception) {
                ""
            }

        if (callId.isBlank()) {
            return
        }

        val peerIp =
            keyStore.getPeerAddress(packet.source).orEmpty()

        if (VoiceCallRegistry.isBusy()) {
            sendCallSignalToPeer(
                targetPeerId = packet.source,
                targetIp = peerIp,
                type = "CALL_BUSY",
                callId = callId
            )
            val summary = "Panggilan dari ${packet.source} ditolak otomatis karena sesi lain masih aktif."
            listener.onCallInviteReceived(summary)
            saveCallNote(
                peerName = packet.source,
                content = context.getString(R.string.call_note_busy_local),
                isSent = false,
                status = "BUSY"
            )
            return
        }

        val intent =
            CallSessionActivity.createIntent(
                context = context,
                peerName = packet.source,
                peerIp = peerIp,
                callId = callId,
                incoming = true
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        AppNotificationManager.notifyIncomingCall(
            context = appContext,
            peerName = packet.source,
            peerIp = peerIp,
            callId = callId
        )
        openIncomingCall(intent)
        listener.onCallInviteReceived("Panggilan masuk dari ${packet.source}")
    }

    private fun handleIncomingChatMessage(
        packet: MeshPacket,
        payload: String
    ) {
        val cleanPayload =
            PacketTtlManager.extractMessage(payload)
                .ifBlank { payload }

        scope.launch(Dispatchers.IO) {
            try {
                if (chatDb.chatDao().countByPacketId(packet.packetId) == 0) {
                    chatDb.chatDao().insertMessage(
                        ChatMessage(
                            packetId = packet.packetId,
                            chatId = packet.source,
                            senderName = packet.source,
                            content = cleanPayload,
                            contentType = "TEXT",
                            isSent = false,
                            status = "RECEIVED"
                        )
                    )
                }

                if (!ChatActivity.isViewingChatWith(packet.source)) {
                    AppNotificationManager.notifyChatMessage(
                        context = appContext,
                        peerName = packet.source,
                        message = cleanPayload.take(120)
                    )
                }
                listener.onChatReceived("${packet.source}: ${cleanPayload.take(120)}")
            } catch (error: Exception) {
                Log.e("GHALBIT", "Incoming chat save failed", error)
                listener.onPacketError("Incoming chat save failed", error)
            }
        }
    }

    private fun sendCallSignalToPeer(
        targetPeerId: String,
        targetIp: String,
        type: String,
        callId: String
    ) {
        if (targetIp.isBlank()) {
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val payload =
                    JSONObject()
                        .put("callId", callId)
                        .put("peerName", localPeerId)
                        .toString()

                ReliablePacketSender.sendWithRetry(
                    targetIp,
                    MeshPacket(
                        packetId = "$type-${System.currentTimeMillis()}",
                        source = localPeerId,
                        destination = targetPeerId,
                        type = type,
                        payload = payload,
                        encrypted = false
                    )
                )
            } catch (error: Exception) {
                Log.e("GHALBIT", "Call signal send failed", error)
                listener.onPacketError("Call signal send failed", error)
            }
        }
    }

    private fun saveCallNote(
        peerName: String,
        content: String,
        isSent: Boolean,
        status: String
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                chatDb.chatDao().insertMessage(
                    ChatMessage(
                        packetId = "CALL-NOTE-${peerName}-${status}-${System.currentTimeMillis()}",
                        chatId = peerName,
                        senderName = if (isSent) "ME" else peerName,
                        content = content,
                        contentType = "CALL",
                        isSent = isSent,
                        status = status
                    )
                )
            } catch (error: Exception) {
                Log.e("GHALBIT", "Call note save failed", error)
                listener.onPacketError("Call note save failed", error)
            }
        }
    }

    private fun handleIncomingSos(
        packet: MeshPacket,
        payload: String
    ) {
        scope.launch(Dispatchers.IO) {
            if (chatDb.chatDao().countByPacketId(packet.packetId) == 0) {
                chatDb.chatDao().insertMessage(
                    ChatMessage(
                        packetId = packet.packetId,
                        chatId = packet.source,
                        senderName = packet.source,
                        content = "SOS ALERT: $payload",
                        contentType = "SOS",
                        isSent = false,
                        status = "RECEIVED"
                    )
                )
            }
        }

        AppNotificationManager.notifySos(
            context = appContext,
            peerName = packet.source,
            payload = payload
        )
        listener.onSosReceived("SOS from ${packet.source}: $payload")
    }

    private fun decryptPayload(packet: MeshPacket): String {
        return try {
            val peerPubKey =
                keyStore.getPeerKey(packet.source)
                    ?: return "[NO KEY]"

            val sharedSecret =
                CryptoEngine.deriveSharedSecret(
                    keyStore.privateKey,
                    CryptoEngine.base64ToPublicKey(peerPubKey)
                )

            val encryptedBytes =
                Base64.decode(packet.payload, Base64.DEFAULT)

            String(
                CryptoEngine.decrypt(
                    encryptedBytes,
                    sharedSecret
                )
            )
        } catch (_: Exception) {
            "[DECRYPT FAILED]"
        }
    }

    private fun sendAck(
        packet: MeshPacket
    ) {
        try {
            val peerIp =
                keyStore.getPeerAddress(packet.source) ?: return

            val ackPacket =
                MeshPacket(
                    packetId = "ACK-" + System.currentTimeMillis(),
                    source = localPeerId,
                    destination = packet.source,
                    type = "ACK",
                    payload = packet.packetId,
                    encrypted = false
                )

            MeshSocketClient.send(
                peerIp,
                ackPacket
            )

            MeshStatistics.sentPacket("ACK")
            listener.onPacketStatus("ACK sent to ${packet.source}")
        } catch (error: Exception) {
            listener.onPacketStatus("ACK failed: ${error.message}")
        }
    }
}

