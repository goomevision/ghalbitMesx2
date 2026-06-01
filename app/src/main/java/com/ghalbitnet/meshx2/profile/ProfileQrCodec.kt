package com.ghalbitnet.meshx2.profile

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONArray
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
            payload.bio,
            payload.community,
            payload.region,
            payload.tier,
            payload.trustScore.toString(),
            payload.trustRank,
            payload.mentorStatus,
            payload.referralLabel,
            payload.communityReputation.toString(),
            payload.profileVersion.toString(),
            payload.relayHint.orEmpty(),
            payload.timestamp.toString()
        ).joinToString("|")
    }

    fun encode(payload: ProfileQrPayload): String {
        Log.d("GHALBIT-CARD-QR", "QR payload created")
        return JSONObject()
            .put("globalId", payload.globalId)
            .put("publicKey", payload.publicKey)
            .put("publicKeyHash", payload.publicKeyHash)
            .put("displayName", payload.displayName)
            .put("nickname", payload.nickname)
            .put("roleTitle", payload.roleTitle)
            .put("bio", payload.bio)
            .put("community", payload.community)
            .put("region", payload.region)
            .put("tier", payload.tier)
            .put("trustScore", payload.trustScore)
            .put("trustRank", payload.trustRank)
            .put("badges", JSONArray(payload.badges))
            .put("mentorStatus", payload.mentorStatus)
            .put("referralLabel", payload.referralLabel)
            .put("communityReputation", payload.communityReputation)
            .put("profileVersion", payload.profileVersion)
            .put("relayHint", payload.relayHint ?: "")
            .put("timestamp", payload.timestamp)
            .put("signature", payload.signature)
            .toString()
    }

    fun decode(raw: String): ProfileQrPayload? {
        return runCatching {
            val json = JSONObject(raw)
            val badges = mutableListOf<String>()
            val badgesArray = json.optJSONArray("badges")
            if (badgesArray != null) {
                for (i in 0 until badgesArray.length()) {
                    badges += badgesArray.optString(i)
                }
            }
            ProfileQrPayload(
                globalId = json.getString("globalId"),
                publicKey = json.getString("publicKey"),
                publicKeyHash = json.optString("publicKeyHash"),
                displayName = json.optString("displayName", "Pengguna GHALBITNET"),
                nickname = json.optString("nickname"),
                roleTitle = json.optString("roleTitle", "Anggota Komunitas"),
                bio = json.optString("bio"),
                community = json.optString("community", "GhalbitNet Community"),
                region = json.optString("region", "Wilayah belum diisi"),
                tier = json.optString("tier", "BASIC"),
                trustScore = json.optInt("trustScore", 0),
                trustRank = json.optString("trustRank", "Baru"),
                badges = badges,
                mentorStatus = json.optString("mentorStatus", "Belum Menjadi Mentor"),
                referralLabel = json.optString("referralLabel", "0/0"),
                communityReputation = json.optInt("communityReputation", 0),
                profileVersion = json.optInt("profileVersion", 1),
                relayHint = json.optString("relayHint").ifBlank { null },
                timestamp = json.optLong("timestamp", 0L),
                signature = json.optString("signature")
            )
        }.onSuccess {
            Log.d("GHALBIT-CARD-QR", "QR decode success")
        }.onFailure {
            Log.w("GHALBIT-CARD-QR", "QR decode failed safely")
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
        Log.d("GHALBIT-CARD-QR", "QR bitmap rendered")
        return bitmap
    }
}
