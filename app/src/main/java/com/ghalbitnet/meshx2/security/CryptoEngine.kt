package com.ghalbitnet.meshx2.security

import android.util.Base64
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoEngine {
    private const val CURVE = "secp256r1"
    private const val AES_ALGO = "AES/GCM/NoPadding"

    fun generateKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec(CURVE), SecureRandom())
        return keyGen.generateKeyPair()
    }

    fun publicKeyToBase64(publicKey: PublicKey): String =
        Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)

    fun base64ToPublicKey(key: String): PublicKey {
        val keyBytes = Base64.decode(key, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(keySpec)
    }

    fun fingerprint(
        publicKeyBase64: String
    ): String {
        if (publicKeyBase64.isBlank()) {
            return "-"
        }

        return try {
            val keyBytes =
                Base64.decode(
                    publicKeyBase64,
                    Base64.NO_WRAP
                )

            MessageDigest
                .getInstance("SHA-256")
                .digest(keyBytes)
                .take(6)
                .joinToString(":") {
                    "%02X".format(it)
                }
        } catch (_: Exception) {
            "-"
        }
    }

    fun deriveSharedSecret(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(publicKey, true)
        return agreement.generateSecret()
    }

    private fun deriveAesKey(sharedSecret: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(sharedSecret)
    }

    fun encrypt(plaintext: ByteArray, sharedSecret: ByteArray): ByteArray {
        val key = SecretKeySpec(deriveAesKey(sharedSecret), "AES")
        val cipher = Cipher.getInstance(AES_ALGO)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    fun decrypt(encryptedData: ByteArray, sharedSecret: ByteArray): ByteArray {
        val key = SecretKeySpec(deriveAesKey(sharedSecret), "AES")
        val iv = encryptedData.copyOfRange(0, 12)
        val ciphertext = encryptedData.copyOfRange(12, encryptedData.size)
        val cipher = Cipher.getInstance(AES_ALGO)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }
}
