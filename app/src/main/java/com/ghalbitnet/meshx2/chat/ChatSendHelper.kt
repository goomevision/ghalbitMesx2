package com.ghalbitnet.meshx2.chat

import android.util.Base64
import com.ghalbitnet.meshx2.MainActivity
import com.ghalbitnet.meshx2.core.log.MeshLogger
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.network.ReliablePacketSender
import com.ghalbitnet.meshx2.routing.PacketTtlManager
import com.ghalbitnet.meshx2.security.CryptoEngine
import com.ghalbitnet.meshx2.security.KeyStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ChatSendHelper {

    suspend fun sendTextMessage(
        keyStore: KeyStoreManager,
        peerName: String,
        peerIp: String,
        message: String,
        packetId: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val securePayload =
                buildChatPayload(
                    keyStore = keyStore,
                    peerName = peerName,
                    message = message
                )

            val packet =
                MeshPacket(
                    packetId = packetId,
                    source = MainActivity.myGlobalPeerId,
                    destination = peerName,
                    type = "CHAT",
                    payload = securePayload.payload,
                    encrypted = securePayload.encrypted
                )

            ReliablePacketSender.sendWithRetry(
                peerIp,
                packet
            )
        }
    }

    private fun buildChatPayload(
        keyStore: KeyStoreManager,
        peerName: String,
        message: String
    ): ChatPayload {
        val plainPayload =
            PacketTtlManager.attachTtl(message)

        val peerPublicKey =
            keyStore.getPeerKey(peerName)

        if (peerPublicKey.isNullOrBlank()) {
            return ChatPayload(
                payload = plainPayload,
                encrypted = false
            )
        }

        return try {
            val sharedSecret =
                CryptoEngine.deriveSharedSecret(
                    keyStore.privateKey,
                    CryptoEngine.base64ToPublicKey(peerPublicKey)
                )

            val encryptedBytes =
                CryptoEngine.encrypt(
                    plainPayload.toByteArray(),
                    sharedSecret
                )

            ChatPayload(
                payload = Base64.encodeToString(
                    encryptedBytes,
                    Base64.NO_WRAP
                ),
                encrypted = true
            )
        } catch (e: Exception) {
            MeshLogger.e(
                "CHAT",
                "Encryption failed; sending plaintext fallback",
                e
            )

            ChatPayload(
                payload = plainPayload,
                encrypted = false
            )
        }
    }

    private data class ChatPayload(
        val payload: String,
        val encrypted: Boolean
    )
}
