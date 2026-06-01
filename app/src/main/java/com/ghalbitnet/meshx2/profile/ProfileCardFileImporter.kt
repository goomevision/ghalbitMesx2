package com.ghalbitnet.meshx2.profile

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONObject

object ProfileCardFileImporter {
    private const val MAX_BYTES = 512 * 1024

    fun readPayloadFromUri(context: Context, uri: Uri): String? {
        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            val capped = if (bytes.size > MAX_BYTES) bytes.copyOf(MAX_BYTES) else bytes
            capped.toString(Charsets.UTF_8)
        } ?: return null
        return extractQrPayload(text)
    }

    private fun extractQrPayload(raw: String): String? {
        val trimmed = raw.trim()
        if (ProfileQrCodec.decode(trimmed) != null) {
            return trimmed
        }

        val globalIdx = trimmed.indexOf("\"globalId\"")
        if (globalIdx >= 0) {
            val start = trimmed.lastIndexOf('{', globalIdx)
            if (start >= 0) {
                var depth = 0
                for (i in start until trimmed.length) {
                    when (trimmed[i]) {
                        '{' -> depth++
                        '}' -> {
                            depth--
                            if (depth == 0) {
                                val candidate = trimmed.substring(start, i + 1)
                                if (runCatching { JSONObject(candidate) }.isSuccess && ProfileQrCodec.decode(candidate) != null) {
                                    Log.d("GHALBIT-CARD-QR", "QR payload fallback used")
                                    return candidate
                                }
                                break
                            }
                        }
                    }
                }
            }
        }
        return null
    }
}
