package com.ghalbitnet.meshx2.chat

import android.content.Context
import com.ghalbitnet.meshx2.core.log.MeshLogger
import com.ghalbitnet.meshx2.identity.GhalbitIdentityRecord
import com.ghalbitnet.meshx2.util.LogThrottle
import org.json.JSONObject

object ConversationIdentityStore {
    private const val PREF_NAME = "ghalbit_conversation_identity"

    fun all(
        context: Context
    ): List<ConversationIdentityMetadata> {
        return prefs(context)
            .all
            .mapNotNull { (chatId, value) ->
                val raw =
                    value as? String ?: return@mapNotNull null

                runCatching {
                    val json = JSONObject(raw)
                    ConversationIdentityMetadata(
                        chatId = chatId,
                        globalId = json.optString("globalId").ifBlank { null },
                        publicKey = json.optString("publicKey").ifBlank { null },
                        publicKeyHash = json.optString("publicKeyHash").ifBlank { null },
                        walletAddress = json.optString("walletAddress").ifBlank { null },
                        canonicalDisplayName = json.optString("canonicalDisplayName").ifBlank { null },
                        lastSeen = json.optLong("lastSeen", 0L).takeIf { it > 0L },
                        routeHint = json.optString("routeHint").ifBlank { null },
                        verificationStatus = runCatching { PeerVerificationStatus.valueOf(json.optString("verificationStatus", PeerVerificationStatus.STALE.name)) }.getOrDefault(PeerVerificationStatus.STALE),
                        updatedAt = json.optLong("updatedAt", 0L)
                    )
                }.getOrNull()
            }
            .sortedByDescending { it.updatedAt }
    }

    fun get(
        context: Context,
        chatId: String
    ): ConversationIdentityMetadata? {
        val raw =
            prefs(context).getString(chatId, null)
                ?: return null

        return runCatching {
            val json = JSONObject(raw)
            ConversationIdentityMetadata(
                chatId = chatId,
                globalId = json.optString("globalId").ifBlank { null },
                publicKey = json.optString("publicKey").ifBlank { null },
                publicKeyHash = json.optString("publicKeyHash").ifBlank { null },
                walletAddress = json.optString("walletAddress").ifBlank { null },
                canonicalDisplayName = json.optString("canonicalDisplayName").ifBlank { null },
                lastSeen = json.optLong("lastSeen", 0L).takeIf { it > 0L },
                routeHint = json.optString("routeHint").ifBlank { null },
                verificationStatus = runCatching { PeerVerificationStatus.valueOf(json.optString("verificationStatus", PeerVerificationStatus.STALE.name)) }.getOrDefault(PeerVerificationStatus.STALE),
                updatedAt = json.optLong("updatedAt", 0L)
            )
        }.getOrNull()
    }

    fun upsert(
        context: Context,
        chatId: String,
        metadata: ConversationIdentityMetadata
    ): ConversationIdentityMetadata {
        val merged =
            merge(
                current = get(context, chatId),
                incoming = metadata.copy(chatId = chatId)
            )

        prefs(context)
            .edit()
            .putString(chatId, toJson(merged).toString())
            .apply()

        if (LogThrottle.shouldLog("conversation-metadata:$chatId:${merged.globalId ?: "-"}", 10_000L, context)) {
            MeshLogger.i(
                "CONVERSATION_IDENTITY_METADATA",
                "chatId=$chatId globalId=${merged.globalId ?: "-"}"
            )
        }

        return merged
    }

    fun upsert(
        context: Context,
        chatId: String,
        identity: GhalbitIdentityRecord
    ): ConversationIdentityMetadata {
        return upsert(
            context = context,
            chatId = chatId,
            metadata = ConversationIdentityMetadata(
                chatId = chatId,
                globalId = identity.globalId,
                publicKey = identity.publicKey,
                publicKeyHash = identity.publicKey?.let { com.ghalbitnet.meshx2.call.CallManager.publicKeyHash(it) },
                walletAddress = identity.walletAddress,
                canonicalDisplayName = identity.displayName,
                lastSeen = identity.lastSeen,
                routeHint = identity.lastKnownIp,
                verificationStatus = if (!identity.publicKey.isNullOrBlank() || !identity.globalId.isNullOrBlank()) PeerVerificationStatus.VERIFIED else PeerVerificationStatus.PROVISIONAL,
                updatedAt = identity.lastSeen
            )
        )
    }

    fun merge(
        current: ConversationIdentityMetadata?,
        incoming: ConversationIdentityMetadata
    ): ConversationIdentityMetadata {
        if (current == null) {
            return incoming.copy(
                updatedAt = maxOf(incoming.updatedAt, System.currentTimeMillis())
            )
        }

        return ConversationIdentityMetadata(
            chatId = current.chatId,
            globalId = chooseText(current.globalId, incoming.globalId),
            publicKey = chooseText(current.publicKey, incoming.publicKey),
            publicKeyHash = chooseText(current.publicKeyHash, incoming.publicKeyHash),
            walletAddress = chooseText(current.walletAddress, incoming.walletAddress),
            canonicalDisplayName = chooseText(incoming.canonicalDisplayName, current.canonicalDisplayName),
            lastSeen = maxOf(current.lastSeen ?: 0L, incoming.lastSeen ?: 0L).takeIf { it > 0L },
            routeHint = chooseText(incoming.routeHint, current.routeHint),
            verificationStatus =
                when {
                    incoming.verificationStatus == PeerVerificationStatus.VERIFIED || current.verificationStatus == PeerVerificationStatus.VERIFIED ->
                        PeerVerificationStatus.VERIFIED
                    incoming.verificationStatus == PeerVerificationStatus.PROVISIONAL || current.verificationStatus == PeerVerificationStatus.PROVISIONAL ->
                        PeerVerificationStatus.PROVISIONAL
                    else -> PeerVerificationStatus.STALE
                },
            updatedAt = maxOf(current.updatedAt, incoming.updatedAt, System.currentTimeMillis())
        )
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun toJson(metadata: ConversationIdentityMetadata): JSONObject {
        return JSONObject().apply {
            put("globalId", metadata.globalId)
            put("publicKey", metadata.publicKey)
            put("publicKeyHash", metadata.publicKeyHash)
            put("walletAddress", metadata.walletAddress)
            put("canonicalDisplayName", metadata.canonicalDisplayName)
            put("lastSeen", metadata.lastSeen)
            put("routeHint", metadata.routeHint)
            put("verificationStatus", metadata.verificationStatus.name)
            put("updatedAt", metadata.updatedAt)
        }
    }

    private fun chooseText(
        preferred: String?,
        fallback: String?
    ): String? {
        return preferred?.takeIf { it.isNotBlank() }
            ?: fallback?.takeIf { it.isNotBlank() }
    }
}
