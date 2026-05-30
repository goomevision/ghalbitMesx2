package com.ghalbitnet.meshx2.profile

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object ProfileQrCodec {
    private data class CacheEntry(val bitmap: Bitmap, val createdAt: Long)
    private val renderCache = ConcurrentHashMap<String, CacheEntry>()

    fun canonicalPayload(payload: ProfileQrPayload): String {
        return listOf(
            payload.globalId,
            payload.publicKey,
            payload.publicKeyHash,
            payload.displayName,
            payload.nickname,
            payload.roleTitle,
            payload.profileVersion.toString(),
            payload.relayHint.orEmpty()
        ).joinToString("|")
    }

    fun encode(payload: ProfileQrPayload): String {
        return JSONObject()
            .put("globalId", payload.globalId)
            .put("publicKey", payload.publicKey)
            .put("publicKeyHash", payload.publicKeyHash)
            .put("displayName", payload.displayName)
            .put("nickname", payload.nickname)
            .put("roleTitle", payload.roleTitle)
            .put("profileVersion", payload.profileVersion)
            .put("relayHint", payload.relayHint ?: "")
            .put("signature", payload.signature)
            .toString()
    }

    fun decode(raw: String): ProfileQrPayload? {
        return runCatching {
            val json = JSONObject(raw)
            ProfileQrPayload(
                globalId = json.getString("globalId"),
                publicKey = json.getString("publicKey"),
                publicKeyHash = json.getString("publicKeyHash"),
                displayName = json.optString("displayName"),
                nickname = json.optString("nickname"),
                roleTitle = json.optString("roleTitle"),
                profileVersion = json.optInt("profileVersion", 1),
                relayHint = json.optString("relayHint").ifBlank { null },
                signature = json.getString("signature")
            )
        }.onSuccess {
            Log.d("GHALBIT-CARD-QR", "verified")
        }.getOrNull()
    }

    fun renderBitmap(raw: String, sizePx: Int = 320): Bitmap {
        val cacheKey = "$sizePx:$raw"
        val cached = renderCache[cacheKey]
        if (cached != null && !cached.bitmap.isRecycled && System.currentTimeMillis() - cached.createdAt < 60_000L) {
            return cached.bitmap
        }
        val matrix = QRCodeWriter().encode(raw, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        renderCache[cacheKey] = CacheEntry(bitmap, System.currentTimeMillis())
        Log.d("GHALBIT-CARD", "qr rendered")
        Log.d("GHALBIT-CARD-QR", "generated")
        return bitmap
    }
}
