package com.ghalbitnet.meshx2.core.utils

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {

    private fun createKey(
        secret: String
    ): SecretKeySpec {

        val bytes =
            MessageDigest
                .getInstance("SHA-256")
                .digest(secret.toByteArray())

        return SecretKeySpec(
            bytes,
            "AES"
        )
    }

    fun encrypt(
        text: String,
        secret: String
    ): String {

        val cipher =
            Cipher.getInstance("AES")

        cipher.init(
            Cipher.ENCRYPT_MODE,
            createKey(secret)
        )

        val encrypted =
            cipher.doFinal(
                text.toByteArray()
            )

        return Base64.encodeToString(
            encrypted,
            Base64.NO_WRAP
        )
    }

    fun decrypt(
        text: String,
        secret: String
    ): String {

        val cipher =
            Cipher.getInstance("AES")

        cipher.init(
            Cipher.DECRYPT_MODE,
            createKey(secret)
        )

        val decoded =
            Base64.decode(
                text,
                Base64.NO_WRAP
            )

        return String(
            cipher.doFinal(decoded)
        )
    }
}
