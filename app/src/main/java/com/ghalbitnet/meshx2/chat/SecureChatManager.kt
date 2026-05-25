package com.ghalbitnet.meshx2.chat

import android.util.Base64
import android.util.Log
import com.ghalbitnet.meshx2.discovery.UdpDiscovery
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.model.SecurePacket
import com.ghalbitnet.meshx2.network.MeshSocketClient
import com.ghalbitnet.meshx2.security.CryptoEngine
import com.ghalbitnet.meshx2.security.KeyStoreManager
import java.util.UUID

object SecureChatManager {
    private val sharedSecrets = mutableMapOf<String, ByteArray>()

    /**
     * Mengirim pesan terenkripsi ke peer dengan Peer ID tertentu.
     * Jika alamat IP atau public key belum diketahui, fungsi akan menolak
     * dan (jika key tidak ada) memicu broadcast ulang agar key segera dikirim.
     */
    fun sendEncryptedMessage(
        message: String,
        destinationPeerId: String,
        keyStore: KeyStoreManager,
        myPeerId: String
    ) {
        // 1. Pastikan kita tahu IP tujuan
        val destIp = keyStore.getPeerAddress(destinationPeerId)
        if (destIp == null) {
            Log.e("GHALBIT", "No IP for peer $destinationPeerId Ã¢â‚¬â€œ waiting for broadcast...")
            return
        }

        // 2. Pastikan kita punya public key tujuan
        val destPubKey = keyStore.getPeerKey(destinationPeerId)
        if (destPubKey == null) {
            Log.e("GHALBIT", "No public key for peer $destinationPeerId Ã¢â‚¬â€œ triggering reÃ¢â‚¬â€˜broadcast")
            // Kirim ulang broadcast kita sendiri agar peer mendengar dan menyimpan key kita,
            // dan juga memicu peer untuk mengirim balik (jika perlu).
            UdpDiscovery.broadcastNode(myPeerId)
            return
        }

        Log.d("GHALBIT", "Encrypting message for $destinationPeerId at $destIp")

        // 3. Dapatkan / hitung shared secret
        val sharedSecret = sharedSecrets.getOrPut(destinationPeerId) {
            CryptoEngine.deriveSharedSecret(
                keyStore.privateKey,
                CryptoEngine.base64ToPublicKey(destPubKey)
            )
        }

        // 4. Enkripsi
        val encrypted = CryptoEngine.encrypt(message.toByteArray(), sharedSecret)
        val encryptedPayload = Base64.encodeToString(encrypted, Base64.NO_WRAP)

        // 5. Buat MeshPacket
        val meshPacket = MeshPacket(
            packetId = UUID.randomUUID().toString(),
            source = myPeerId,
            destination = destinationPeerId,
            type = "MESH_PACKET",
            payload = encryptedPayload,
            hopCount = 0,
            maxHop = 5,
            timestamp = System.currentTimeMillis(),
            encrypted = true
        )

        // 6. Kirim via TCP
        MeshSocketClient.send(destIp, meshPacket)
        Log.d("GHALBIT", "Encrypted MeshPacket sent to $destIp")
    }

    /**
     * Mendekripsi SecurePacket yang diterima dari node Android lain.
     * Digunakan oleh MessagingReceiver.
     */
    fun decryptReceivedPacket(securePacket: SecurePacket, keyStore: KeyStoreManager): String? {
        return try {
            val senderPubKey = CryptoEngine.base64ToPublicKey(securePacket.sourcePublicKey)
            val sharedSecret = sharedSecrets.getOrPut(securePacket.sourcePublicKey) {
                CryptoEngine.deriveSharedSecret(keyStore.privateKey, senderPubKey)
            }
            val encryptedBytes = Base64.decode(securePacket.encryptedPayload, Base64.NO_WRAP)
            String(CryptoEngine.decrypt(encryptedBytes, sharedSecret))
        } catch (e: Exception) {
            Log.e("GHALBIT", "Decryption failed: ${e.message}")
            null
        }
    }
}