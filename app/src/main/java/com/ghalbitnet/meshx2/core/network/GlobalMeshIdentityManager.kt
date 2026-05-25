package com.ghalbitnet.meshx2.core.network

import android.util.Base64
import java.security.MessageDigest

object GlobalMeshIdentityManager {

    fun buildGlobalId(publicKeyBase64: String): String {
        if (publicKeyBase64.isBlank()) {
            return "GX-UNKNOWN"
        }

        val decoded =
            try {
                Base64.decode(publicKeyBase64, Base64.DEFAULT)
            } catch (_: Exception) {
                publicKeyBase64.toByteArray()
            }

        val digest =
            MessageDigest.getInstance("SHA-256")
                .digest(decoded)

        val hex =
            buildString {
                digest.forEach { byte ->
                    append(String.format("%02x", byte))
                }
            }.uppercase()

        return "GX-${hex.take(12)}"
    }
}
