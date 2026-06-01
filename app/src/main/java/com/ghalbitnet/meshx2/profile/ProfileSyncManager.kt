package com.ghalbitnet.meshx2.profile

import android.content.Context
import android.net.Uri
import android.util.Log
import com.ghalbitnet.meshx2.BuildConfig
import com.ghalbitnet.meshx2.security.NodeSigningIdentityManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

object ProfileSyncManager {
    private const val TAG = "GHALBIT-PROFILE-SYNC"

    fun profilePayloadJson(entity: MyProfileEntity): String {
        return JSONObject()
            .put("globalId", entity.globalId)
            .put("publicKeyHash", entity.publicKeyHash)
            .put("displayName", entity.displayName)
            .put("nickname", entity.nickname)
            .put("communityName", entity.communityName)
            .put("roleTitle", entity.roleTitle)
            .put("bio", entity.bio)
            .put("region", entity.region)
            .put("organization", entity.organization ?: "")
            .put("skillTags", JSONArray(entity.skillTagsCsv.split(',').mapNotNull { it.trim().takeIf(String::isNotBlank) }))
            .put("avatarUri", sanitizeAvatarUri(entity.avatarUri, entity.avatarSyncEnabled))
            .put("bannerColor", entity.bannerColor)
            .put("cardTheme", entity.cardThemeId)
            .put("statusMessage", entity.statusMessage)
            .put("statusType", entity.statusType)
            .put("statusUpdatedAt", entity.statusUpdatedAt)
            .put("statusExpiresAt", entity.statusExpiresAt ?: JSONObject.NULL)
            .put("updatedAt", entity.updatedAt)
            .put("profileVersion", entity.profileVersion)
            .put("visibility", if (entity.isPublicProfile) "PUBLIC" else "PRIVATE")
            .put("showRegion", entity.showRegionPublicly)
            .put("showStatus", entity.showStatusPublicly)
            .put("relayDiscoveryEnabled", entity.relayDiscoveryEnabled)
            .put("avatarSyncEnabled", entity.avatarSyncEnabled)
            .toString()
    }

    fun uploadMyProfile(context: Context): Boolean {
        if (BuildConfig.BASE_RELAY_URL.isBlank()) {
            Log.w(TAG, "upload skipped relay missing")
            return false
        }
        val profile = ProfileRepository.getOrCreateMyProfile(context)
        if (!profile.isPublicProfile || !profile.isRelayDiscoveryEnabled) {
            return false
        }
        val body = JSONObject().apply {
            put("senderGlobalId", profile.globalId)
            put("senderPublicKey", profile.publicKeyBase64)
            put("publicKeyHash", profile.publicKeyHash)
            put("profileVersion", profile.profileVersion)
            put("updatedAt", profile.updatedAt)
            put("signature", profile.signature)
            put("profileJson", profilePayloadJson(profile.toEntity()))
            put("visibility", if (profile.isPublicProfile) "PUBLIC" else "PRIVATE")
        }
        Log.d(TAG, "upload start")
        val response = postJson("${BuildConfig.BASE_RELAY_URL.trimEnd('/')}/profile/update", body)
        val ok = response?.optBoolean("ok") == true
        if (ok) {
            Log.d(TAG, "upload ok")
        }
        return ok
    }

    fun fetchProfile(context: Context, globalId: String): CommunityProfile? {
        if (BuildConfig.BASE_RELAY_URL.isBlank()) {
            return null
        }
        Log.d(TAG, "fetch id=$globalId")
        val response = getJson("${BuildConfig.BASE_RELAY_URL.trimEnd('/')}/profile/$globalId") ?: return null
        val profileJson = response.optJSONObject("profile") ?: return null
        return applyRemoteProfile(context, profileJson)
    }

    fun batchSyncProfiles(context: Context, globalIds: List<String>): Int {
        val ids = globalIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty() || BuildConfig.BASE_RELAY_URL.isBlank()) return 0
        Log.d(TAG, "batch count=${ids.size}")
        val encoded = URLEncoder.encode(ids.joinToString(","), "UTF-8")
        val response = getJson("${BuildConfig.BASE_RELAY_URL.trimEnd('/')}/profile/batch?ids=$encoded") ?: return 0
        val items = response.optJSONArray("profiles") ?: return 0
        var count = 0
        for (index in 0 until items.length()) {
            val json = items.optJSONObject(index) ?: continue
            if (applyRemoteProfile(context, json) != null) {
                count += 1
            }
        }
        return count
    }

    fun verifyProfile(context: Context, globalId: String): Boolean {
        if (BuildConfig.BASE_RELAY_URL.isBlank()) return false
        val response = postJson(
            "${BuildConfig.BASE_RELAY_URL.trimEnd('/')}/profile/verify",
            JSONObject().put("globalId", globalId)
        )
        val verified = response?.optBoolean("verified") == true
        if (verified) {
            fetchProfile(context, globalId)
        }
        return verified
    }

    fun buildQrPayload(profile: CommunityProfile, relayHint: String?): ProfileQrPayload {
        val mapped = ProfessionalCardDataMapper.fromProfile(profile)
        return ProfileQrPayload(
            globalId = mapped.model.globalId,
            publicKey = profile.publicKeyBase64,
            publicKeyHash = mapped.model.publicKeyHash,
            displayName = mapped.model.displayName,
            nickname = mapped.model.nickname,
            roleTitle = mapped.model.role,
            bio = mapped.model.bio,
            community = mapped.model.community,
            region = mapped.model.region,
            tier = mapped.model.tier.name,
            trustScore = mapped.model.trustScore,
            trustRank = mapped.model.trustRank,
            badges = mapped.model.badges,
            mentorStatus = mapped.model.mentorStatus,
            referralLabel = mapped.model.referralLabel,
            communityReputation = mapped.model.communityReputation,
            profileVersion = mapped.model.profileVersion,
            relayHint = relayHint,
            timestamp = mapped.model.updatedAt,
            signature = mapped.model.signature
        )
    }

    fun buildSignedQrPayload(context: Context, profile: CommunityProfile, relayHint: String?): ProfileQrPayload {
        val unsigned = buildQrPayload(profile, relayHint).copy(signature = "")
        val signature = NodeSigningIdentityManager.sign(context, ProfileQrCodec.canonicalPayload(unsigned), profile.globalId)
        return unsigned.copy(signature = signature)
    }

    fun applyScannedQr(context: Context, payload: ProfileQrPayload): CommunityProfile? {
        val verificationStatus = when {
            payload.signature.isBlank() -> ProfileVerificationStatus.UNSIGNED
            payload.publicKey.isBlank() -> ProfileVerificationStatus.UNKNOWN
            NodeSigningIdentityManager.verify(
                payload.publicKey,
                ProfileQrCodec.canonicalPayload(payload.copy(signature = "")),
                payload.signature
            ) -> ProfileVerificationStatus.VALID_SIGNATURE
            else -> ProfileVerificationStatus.INVALID_SIGNATURE
        }
        if (verificationStatus == ProfileVerificationStatus.INVALID_SIGNATURE) {
            Log.w("GHALBIT-CARD-QR", "QR decode failed safely")
            return null
        }
        val remote = ContactProfileEntity(
            globalId = payload.globalId,
            publicKeyBase64 = payload.publicKey,
            publicKeyHash = payload.publicKeyHash,
            publicDisplayName = payload.displayName,
            publicNickname = payload.nickname,
            communityName = payload.community,
            roleTitle = payload.roleTitle,
            bio = payload.bio,
            region = payload.region,
            profileVersion = payload.profileVersion,
            signature = payload.signature,
            visibility = "PUBLIC",
            routeHint = payload.relayHint,
            verifiedAt = if (verificationStatus == ProfileVerificationStatus.VALID_SIGNATURE) System.currentTimeMillis() else null
        )
        ProfileRepository.upsertRemoteProfile(context, remote)
        Log.d("GHALBIT-CARD-QR", "saved")
        return ProfileRepository.getResolvedContact(
            context = context,
            globalId = payload.globalId,
            chatId = payload.globalId,
            fallbackDisplayName = payload.displayName,
            publicKeyHash = payload.publicKeyHash,
            routeHint = payload.relayHint
        )
    }

    private fun applyRemoteProfile(context: Context, json: JSONObject): CommunityProfile? {
        val entity = ContactProfileEntity(
            globalId = json.optString("globalId"),
            publicKeyBase64 = json.optString("senderPublicKey"),
            publicKeyHash = json.optString("publicKeyHash"),
            publicDisplayName = json.optString("displayName"),
            publicNickname = json.optString("nickname"),
            communityName = json.optString("communityName"),
            roleTitle = json.optString("roleTitle"),
            bio = json.optString("bio"),
            region = json.optString("region"),
            organization = json.optString("organization").ifBlank { null },
            skillTagsCsv = json.optJSONArray("skillTags")?.let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        add(array.optString(index))
                    }
                }.joinToString(",")
            } ?: "",
            avatarUri = json.optString("avatarUri").ifBlank { null },
            bannerColor = json.optString("bannerColor").ifBlank { ContactCardTheme.OCEAN.accentColor },
            cardThemeId = json.optString("cardTheme").ifBlank { ContactCardTheme.OCEAN.themeId },
            statusMessage = json.optString("statusMessage"),
            statusType = json.optString("statusType").ifBlank { CommunityStatusType.AVAILABLE.wireValue },
            statusUpdatedAt = json.optLong("statusUpdatedAt"),
            statusExpiresAt = json.optLong("statusExpiresAt").takeIf { it > 0L },
            updatedAt = json.optLong("updatedAt"),
            profileVersion = json.optInt("profileVersion", 1),
            signature = json.optString("signature"),
            visibility = json.optString("visibility").ifBlank { "PRIVATE" },
            routeHint = json.optString("routeHint").ifBlank { null },
            verifiedAt = System.currentTimeMillis()
        )
        if (entity.globalId.isBlank()) return null
        ProfileRepository.upsertRemoteProfile(context, entity)
        Log.d(TAG, "signature ok")
        return ProfileRepository.getResolvedContact(
            context = context,
            globalId = entity.globalId,
            chatId = entity.globalId,
            fallbackDisplayName = entity.publicDisplayName,
            publicKeyHash = entity.publicKeyHash,
            routeHint = entity.routeHint
        )
    }

    private fun postJson(url: String, body: JSONObject): JSONObject? {
        return runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.doOutput = true
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(body.toString())
            }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream.bufferedReader().use { it.readText() }
            JSONObject(text)
        }.getOrNull()
    }

    private fun getJson(url: String): JSONObject? {
        return runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(text)
        }.getOrNull()
    }

    private fun sanitizeAvatarUri(avatarUri: String?, allowed: Boolean): String {
        if (!allowed) return ""
        val value = avatarUri?.trim().orEmpty()
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value
        }
        return ""
    }

    private fun CommunityProfile.toEntity(): MyProfileEntity {
        return MyProfileEntity(
            globalId = globalId,
            publicKeyBase64 = publicKeyBase64,
            publicKeyHash = publicKeyHash,
            displayName = displayName,
            nickname = nickname,
            communityName = communityName,
            roleTitle = roleTitle,
            bio = bio,
            region = region,
            organization = organization,
            skillTagsCsv = skillTags.joinToString(","),
            avatarUri = avatarUri?.let { runCatching { Uri.parse(it).toString() }.getOrNull() },
            bannerColor = bannerColor,
            cardThemeId = cardTheme.themeId,
            statusMessage = statusMessage,
            statusType = statusType.wireValue,
            statusUpdatedAt = statusUpdatedAt,
            statusExpiresAt = statusExpiresAt,
            updatedAt = updatedAt,
            profileVersion = profileVersion,
            signature = signature,
            isPublicProfile = isPublicProfile,
            showPhonePublicly = false,
            showRegionPublicly = isRegionVisible,
            showStatusPublicly = isStatusVisible,
            relayDiscoveryEnabled = isRelayDiscoveryEnabled,
            avatarSyncEnabled = isAvatarSyncEnabled,
            relaySyncEnabled = isRelayDiscoveryEnabled
        )
    }
}


