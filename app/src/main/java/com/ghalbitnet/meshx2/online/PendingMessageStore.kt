package com.ghalbitnet.meshx2.online

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

object PendingMessageStore {
    private const val LEGACY_PREFS = "ghalbit_pending_message_store"
    private const val LEGACY_KEY_ITEMS = "items"
    private const val MIGRATION_PREFS = "ghalbit_pending_room_migration"
    private const val KEY_IMPORTED = "imported_v1"

    fun all(context: Context): List<PendingMessage> = blocking(context) { dao ->
        dao.allMessages().map { entity -> toPending(entity, dao.findMedia(entity.packetId), retry = dao.allRetrySchedules().firstOrNull { it.packetId == entity.packetId }) }
    }

    fun upsert(context: Context, message: PendingMessage) {
        blocking(context) { dao ->
            dao.upsertMessage(
                PendingMessageEntity(
                    packetId = message.packetId,
                    messageId = message.messageId,
                    chatId = message.chatId,
                    targetNodeId = message.targetNodeId,
                    targetGlobalId = message.targetGlobalId,
                    content = message.content,
                    createdAt = message.createdAt,
                    expiresAt = message.expiresAt,
                    deliveryStatus = message.deliveryStatus.name,
                    routeHint = message.routeHint,
                    peerPublicKey = message.peerPublicKey,
                    peerWalletAddress = message.peerWalletAddress,
                    peerDisplayName = message.peerDisplayName,
                    lastFailureReason = message.lastFailureReason
                )
            )
            dao.upsertRetrySchedule(
                RetryScheduleEntity(
                    packetId = message.packetId,
                    retryAttempt = message.retryAttempt,
                    lastAttemptAt = message.lastAttemptAt,
                    nextRetryAt = message.nextRetryAt,
                    expiresAt = message.expiresAt,
                    category = if (message.mediaUri.isNullOrBlank()) "MESSAGE" else "MEDIA",
                    priority = if (message.mediaUri.isNullOrBlank()) 5 else 8
                )
            )
            if (!message.mediaUri.isNullOrBlank() || !message.remoteMediaId.isNullOrBlank() || !message.uploadSessionId.isNullOrBlank()) {
                dao.upsertMedia(
                    PendingMediaEntity(
                        packetId = message.packetId,
                        mediaUri = message.mediaUri,
                        mediaType = message.mediaType,
                        mimeType = message.mimeType,
                        fileSize = message.fileSize,
                        mediaChecksum = message.mediaChecksum,
                        chunkCount = message.chunkCount,
                        uploadedChunks = JSONArray(message.uploadedChunks.sorted()).toString(),
                        uploadSessionId = message.uploadSessionId,
                        remoteMediaId = message.remoteMediaId,
                        secureMediaToken = message.secureMediaToken,
                        uploadState = message.uploadState,
                        lastProgressAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun remove(context: Context, packetId: String) {
        blocking(context) { dao ->
            dao.deletePending(packetId)
        }
    }

    fun find(context: Context, packetId: String): PendingMessage? = blocking(context) { dao ->
        val message = dao.findMessage(packetId) ?: return@blocking null
        toPending(message, dao.findMedia(packetId), dao.allRetrySchedules().firstOrNull { it.packetId == packetId })
    }

    fun countForChat(context: Context, chatId: String): Int = blocking(context) { dao ->
        dao.countForChat(chatId)
    }

    fun mediaItems(context: Context): List<PendingMessage> = blocking(context) { dao ->
        val mediaByPacket = dao.allMedia().associateBy { it.packetId }
        dao.allMessages()
            .filter { mediaByPacket.containsKey(it.packetId) }
            .map { entity -> toPending(entity, mediaByPacket[entity.packetId], dao.allRetrySchedules().firstOrNull { it.packetId == entity.packetId }) }
    }

    fun cleanupExpired(context: Context): Int = blocking(context) { dao ->
        val now = System.currentTimeMillis()
        val expired = dao.allMessages().count { it.expiresAt > 0L && now > it.expiresAt }
        dao.cleanupOrphanMedia()
        dao.cleanupOrphanSchedules()
        if (expired > 0) {
            Log.d("GHALBIT-DELIVERY-PENDING", "expiredFailed candidates=$expired preserveForFinalState=true")
        }
        expired
    }

    private fun <T> blocking(context: Context, block: (PendingMessageDao) -> T): T {
        ensureMigrated(context.applicationContext)
        return runBlocking(Dispatchers.IO) {
            block(PendingDatabase.getInstance(context.applicationContext).pendingMessageDao())
        }
    }

    private fun ensureMigrated(context: Context) {
        val prefs = context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_IMPORTED, false)) {
            return
        }
        synchronized(this) {
            if (prefs.getBoolean(KEY_IMPORTED, false)) {
                return
            }
            Log.d("GHALBIT-PENDING", "migration start")
            val legacyItems = loadLegacy(context)
            if (legacyItems.isNotEmpty()) {
                val dao = PendingDatabase.getInstance(context).pendingMessageDao()
                legacyItems.forEach { upsertInternal(dao, it) }
            }
            prefs.edit().putBoolean(KEY_IMPORTED, true).apply()
            Log.d("GHALBIT-PENDING", "migration complete imported=${legacyItems.size}")
        }
    }

    private fun upsertInternal(dao: PendingMessageDao, message: PendingMessage) {
        dao.upsertMessage(
            PendingMessageEntity(
                packetId = message.packetId,
                messageId = message.messageId,
                chatId = message.chatId,
                targetNodeId = message.targetNodeId,
                targetGlobalId = message.targetGlobalId,
                content = message.content,
                createdAt = message.createdAt,
                expiresAt = message.expiresAt,
                deliveryStatus = message.deliveryStatus.name,
                routeHint = message.routeHint,
                peerPublicKey = message.peerPublicKey,
                peerWalletAddress = message.peerWalletAddress,
                peerDisplayName = message.peerDisplayName,
                lastFailureReason = message.lastFailureReason
            )
        )
        dao.upsertRetrySchedule(
            RetryScheduleEntity(
                packetId = message.packetId,
                retryAttempt = message.retryAttempt,
                lastAttemptAt = message.lastAttemptAt,
                nextRetryAt = message.nextRetryAt,
                expiresAt = message.expiresAt,
                category = if (message.mediaUri.isNullOrBlank()) "MESSAGE" else "MEDIA",
                priority = if (message.mediaUri.isNullOrBlank()) 5 else 8
            )
        )
        if (!message.mediaUri.isNullOrBlank()) {
            dao.upsertMedia(
                PendingMediaEntity(
                    packetId = message.packetId,
                    mediaUri = message.mediaUri,
                    mediaType = message.mediaType,
                    mimeType = message.mimeType,
                    fileSize = message.fileSize,
                    mediaChecksum = message.mediaChecksum,
                    chunkCount = message.chunkCount,
                    uploadedChunks = JSONArray(message.uploadedChunks.sorted()).toString(),
                    uploadSessionId = message.uploadSessionId,
                    remoteMediaId = message.remoteMediaId,
                    secureMediaToken = message.secureMediaToken,
                    uploadState = message.uploadState,
                    lastProgressAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun loadLegacy(context: Context): List<PendingMessage> {
        val raw = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).getString(LEGACY_KEY_ITEMS, "[]").orEmpty()
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    PendingMessage(
                        packetId = item.optString("packetId"),
                        messageId = item.optString("messageId").ifBlank { item.optString("packetId") },
                        chatId = item.optString("chatId"),
                        targetNodeId = item.optString("targetNodeId"),
                        targetGlobalId = item.optString("targetGlobalId").ifBlank { null },
                        content = item.optString("content"),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                        expiresAt = item.optLong("expiresAt", 0L),
                        deliveryStatus = runCatching { DeliveryStatus.valueOf(item.optString("deliveryStatus", DeliveryStatus.PENDING_SYNC.name)) }.getOrDefault(DeliveryStatus.PENDING_SYNC),
                        retryAttempt = item.optInt("retryAttempt", 0),
                        lastAttemptAt = item.optLong("lastAttemptAt", 0L),
                        nextRetryAt = item.optLong("nextRetryAt", 0L),
                        routeHint = item.optString("routeHint").ifBlank { null },
                        peerPublicKey = item.optString("peerPublicKey").ifBlank { null },
                        peerWalletAddress = item.optString("peerWalletAddress").ifBlank { null },
                        peerDisplayName = item.optString("peerDisplayName").ifBlank { null },
                        lastFailureReason = item.optString("lastFailureReason").ifBlank { null },
                        mediaUri = item.optString("mediaUri").ifBlank { null },
                        mediaType = item.optString("mediaType").ifBlank { null },
                        mimeType = item.optString("mimeType").ifBlank { null },
                        fileSize = item.optLong("fileSize", 0L),
                        mediaChecksum = item.optString("mediaChecksum").ifBlank { null },
                        chunkCount = item.optInt("chunkCount", 0),
                        uploadedChunks = parseUploadedChunks(item.optString("uploadedChunks")),
                        uploadSessionId = item.optString("uploadSessionId").ifBlank { null },
                        remoteMediaId = item.optString("remoteMediaId").ifBlank { null },
                        secureMediaToken = item.optString("secureMediaToken").ifBlank { null },
                        uploadState = item.optString("uploadState").ifBlank { null }
                    )
                )
            }
        }
    }

    private fun toPending(
        entity: PendingMessageEntity,
        media: PendingMediaEntity?,
        retry: RetryScheduleEntity?
    ): PendingMessage =
        PendingMessage(
            packetId = entity.packetId,
            messageId = entity.messageId,
            chatId = entity.chatId,
            targetNodeId = entity.targetNodeId,
            targetGlobalId = entity.targetGlobalId,
            content = entity.content,
            createdAt = entity.createdAt,
            expiresAt = entity.expiresAt,
            deliveryStatus = runCatching { DeliveryStatus.valueOf(entity.deliveryStatus) }.getOrDefault(DeliveryStatus.PENDING_SYNC),
            retryAttempt = retry?.retryAttempt ?: 0,
            lastAttemptAt = retry?.lastAttemptAt ?: 0L,
            nextRetryAt = retry?.nextRetryAt ?: 0L,
            routeHint = entity.routeHint,
            peerPublicKey = entity.peerPublicKey,
            peerWalletAddress = entity.peerWalletAddress,
            peerDisplayName = entity.peerDisplayName,
            lastFailureReason = entity.lastFailureReason,
            mediaUri = media?.mediaUri,
            mediaType = media?.mediaType,
            mimeType = media?.mimeType,
            fileSize = media?.fileSize ?: 0L,
            mediaChecksum = media?.mediaChecksum,
            chunkCount = media?.chunkCount ?: 0,
            uploadedChunks = parseUploadedChunks(media?.uploadedChunks),
            uploadSessionId = media?.uploadSessionId,
            remoteMediaId = media?.remoteMediaId,
            secureMediaToken = media?.secureMediaToken,
            uploadState = media?.uploadState
        )

    private fun parseUploadedChunks(raw: String?): Set<Int> {
        if (raw.isNullOrBlank()) return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            buildSet {
                for (i in 0 until arr.length()) add(arr.optInt(i))
            }
        }.getOrDefault(emptySet())
    }
}
