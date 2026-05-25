package com.ghalbitnet.meshx2.core.security

import android.util.Base64

object MeshCipher {

    private const val KEY = "GHALBIT"

    fun encrypt(data: String): String {

        val mixed =
            data + KEY

        return Base64.encodeToString(
            mixed.toByteArray(),
            Base64.NO_WRAP
        )
    }

    fun decrypt(data: String): String {

        return try {

            val decoded =
                String(
                    Base64.decode(
                        data,
                        Base64.NO_WRAP
                    )
                )

            decoded.replace(KEY,"")

        } catch (e: Exception) {

            ""
        }
    }
}