package com.ghalbitnet.meshx2.chat

import com.ghalbitnet.meshx2.model.SecurePacket
import com.ghalbitnet.meshx2.security.KeyStoreManager

object MessagingReceiver {
    fun onSecurePacket(secure: SecurePacket, keyStore: KeyStoreManager, chatDb: ChatDatabase) {
        val plaintext = SecureChatManager.decryptReceivedPacket(secure, keyStore) ?: return
        val chatId = "Me:${secure.sourcePublicKey.take(8)}"
        val msg = ChatMessage(
            chatId = chatId,
            senderName = secure.sourcePublicKey.take(8),
            content = plaintext,
            isSent = false
        )
        kotlinx.coroutines.runBlocking {
            chatDb.chatDao().insertMessage(msg)
        }
    }
}