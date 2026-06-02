package com.ghalbitnet.meshx2.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ghalbitnet.meshx2.MainActivity
import com.ghalbitnet.meshx2.call.CallManager
import com.ghalbitnet.meshx2.discovery.DiscoveryManager
import com.ghalbitnet.meshx2.file.FileTransferManager
import com.ghalbitnet.meshx2.identity.CentralIdentityResolver
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.network.MeshSocketClient
import com.ghalbitnet.meshx2.core.utils.AppNotificationManager
import com.ghalbitnet.meshx2.diagnostics.evidence.RuntimeEvidenceCollector
import com.ghalbitnet.meshx2.diagnostics.evidence.RuntimeEvidenceTags
import com.ghalbitnet.meshx2.online.OnlinePresenceManager
import com.ghalbitnet.meshx2.online.DeliveryStatus
import com.ghalbitnet.meshx2.online.OnlineFallbackTransport
import com.ghalbitnet.meshx2.online.PendingMessage
import com.ghalbitnet.meshx2.online.PendingMessageStore
import com.ghalbitnet.meshx2.online.RelayInboxResult
import com.ghalbitnet.meshx2.online.RelayInboxMessage
import com.ghalbitnet.meshx2.online.RelayInboxReceipt
import com.ghalbitnet.meshx2.online.RelayInboxEdit
import com.ghalbitnet.meshx2.online.RelayInboxDelete
import com.ghalbitnet.meshx2.online.RelayRealtimeChannel
import com.ghalbitnet.meshx2.online.RemoteMediaRelayManager
import com.ghalbitnet.meshx2.security.CryptoEngine
import com.ghalbitnet.meshx2.security.KeyStoreManager
import com.ghalbitnet.meshx2.sos.SosAlertManager
import com.ghalbitnet.meshx2.ui.RuntimeUiState
import com.ghalbitnet.meshx2.ui.RuntimeUiStateManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class ChatDeliveryRequest(
    val chatId: String,
    val peerIp: String,
    val message: String,
    val packetId: String,
    val messageId: String,
    val peerGlobalId: String? = null,
    val peerPublicKey: String? = null,
    val peerWalletAddress: String? = null,
    val peerDisplayName: String? = null
)

data class ChatDeliveryDryRunReport(
    val engineReady: Boolean,
    val pendingQueueReady: Boolean,
    val dedupReady: Boolean,
    val retryReady: Boolean,
    val routeSwitchReady: Boolean,
    val summary: String
)

data class ChatBackgroundSyncReport(
    val inboxMessages: Int,
    val inboxReceipts: Int,
    val pendingMessagesRetried: Int,
    val pendingMediaRetried: Int,
    val skippedBatteryAware: Boolean
)

object ChatDeliveryManager {
    private const val MESSAGE_GRACE_PERIOD_MS = 24 * 60 * 60 * 1000L
    private val mediaRetryBackoffMs = listOf(30_000L, 120_000L, 300_000L, 900_000L, 1_800_000L, 3_600_000L)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val retryMutex = Mutex()
    private val activeAttempts = ConcurrentHashMap<String, Int>()
    private val activeMediaTransfers = ConcurrentHashMap<String, Boolean>()
    private val recentInboundIds = ConcurrentHashMap<String, Long>()
    private const val MAX_RECENT_IDS = 120
    private val retryBackoffMs = listOf(30_000L, 120_000L, 300_000L, 900_000L, 1_800_000L, 3_600_000L)
    private const val MIN_PENDING_SCAN_INTERVAL_MS = 20_000L
    private const val LOG_THROTTLE_MS = 45_000L
    private const val MAX_RETRY_BUDGET_PER_HOUR = 6
    private val syncInFlight = AtomicBoolean(false)
    private var lastScanAt = 0L
    private var lastRelayMissingLogAt = 0L
    private var lastWaitingRouteLogAt = 0L
    private var lastSingleWorkerLogAt = 0L
    private var lastSummaryLogAt = 0L

    private val _lastDryRun =
        MutableStateFlow(
            ChatDeliveryDryRunReport(
                engineReady = false,
                pendingQueueReady = true,
                dedupReady = true,
                retryReady = true,
                routeSwitchReady = true,
                summary = "Belum diuji"
            )
        )
    val lastDryRun: StateFlow<ChatDeliveryDryRunReport> = _lastDryRun

    @Volatile
    private var bound = false

    @Volatile
    private var realtimeListenerBound = false

    fun bind(context: Context) {
        if (bound) {
            throttledLog("single-worker", "GHALBIT-PENDING-GUARD", "single worker active")
            return
        }
        bound = true
        val appContext = context.applicationContext
        bindRealtime(appContext)
        scope.launch {
            while (true) {
                runCatching {
                    bindRealtime(appContext)
                    syncNow(appContext, reason = "bind-loop")
                }.onFailure {
                    Log.e("GHALBIT-CHAT-PENDING", "flush pending failed", it)
                }
                kotlinx.coroutines.delay(com.ghalbitnet.meshx2.online.PowerAwareSyncManager.snapshot(appContext).inboxPollIntervalMs.coerceAtLeast(MIN_PENDING_SCAN_INTERVAL_MS))
            }
        }
    }

    suspend fun syncNow(context: Context, reason: String = "manual"): ChatBackgroundSyncReport {
        if (!syncInFlight.compareAndSet(false, true)) {
            throttledLog("single-worker", "GHALBIT-PENDING-GUARD", "single worker active")
            return ChatBackgroundSyncReport(0, 0, 0, 0, false)
        }
        val now = System.currentTimeMillis()
        val power = com.ghalbitnet.meshx2.online.PowerAwareSyncManager.snapshot(context)
        if (now - lastScanAt < MIN_PENDING_SCAN_INTERVAL_MS) {
            throttledLog("scan", "GHALBIT-PENDING-GUARD", "log throttled")
            syncInFlight.set(false)
            return ChatBackgroundSyncReport(0, 0, 0, 0, false)
        }
        lastScanAt = now
        Log.d("GHALBIT-BG", "sync start")
        try {
        bindRealtime(context.applicationContext)
        PendingMessageStore.cleanupExpired(context)
        val beforeItems = PendingMessageStore.all(context)
        val pendingBefore = beforeItems.count { !it.mediaUri.isNullOrBlank() }
        val messageBefore = beforeItems.count { it.mediaUri.isNullOrBlank() }
        flushPending(context)
        flushPendingMedia(context)
        val inbox =
            if (!RelayRealtimeChannel.isConnected()) {
                Log.d("GHALBIT-REALTIME", "fallback polling")
                syncInternetInbox(context)
            } else {
                RelayInboxResult(emptyList(), emptyList(), emptyList(), emptyList())
            }
        val allAfter = PendingMessageStore.all(context)
        val pendingAfter = allAfter.count { !it.mediaUri.isNullOrBlank() }
        val messageAfter = allAfter.count { it.mediaUri.isNullOrBlank() }
        Log.d(
            "GHALBIT-BG",
            "sync complete reason=$reason inbox=${inbox.messages.size + inbox.receipts.size + inbox.edits.size + inbox.deletes.size} pendingMessages=${messageBefore - messageAfter} pendingMedia=${pendingBefore - pendingAfter}"
        )
        return ChatBackgroundSyncReport(
            inboxMessages = inbox.messages.size,
            inboxReceipts = inbox.receipts.size,
            pendingMessagesRetried = (messageBefore - messageAfter).coerceAtLeast(0),
            pendingMediaRetried = (pendingBefore - pendingAfter).coerceAtLeast(0),
            skippedBatteryAware = power.lowPowerMode
        )
        } finally {
            syncInFlight.set(false)
        }
    }

    private fun bindRealtime(context: Context) {
        if (!realtimeListenerBound) {
            realtimeListenerBound = true
            RelayRealtimeChannel.addListener { event ->
                scope.launch {
                    event.message?.let { handleRemoteRelayMessage(context, it) }
                    event.receipt?.let { handleRemoteRelayReceipt(context, it) }
                    event.edit?.let { handleRemoteRelayEdit(context, it) }
                    event.delete?.let { handleRemoteRelayDelete(context, it) }
                    event.presence?.let { OnlinePresenceManager.applyRealtimePresence(context, it) }
                }
            }
        }
        val localGlobalId = com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager.localGlobalId()
        if (localGlobalId.isNotBlank()) {
            RelayRealtimeChannel.bind(context, localGlobalId)
        }
    }

    fun sendTextMessage(
        context: Context,
        keyStore: KeyStoreManager,
        request: ChatDeliveryRequest
    ) {
        bind(context)
        RuntimeEvidenceCollector.record(
            context,
            RuntimeEvidenceTags.MESSAGE_CREATED,
            source = "ChatDeliveryManager",
            messageId = request.messageId,
            peerId = request.chatId,
            status = "CREATED"
        )
        scope.launch {
            upsertOutgoingMessage(
                context = context,
                request = request,
                state = ChatDeliveryState.QUEUED
            )
            PendingMessageStore.upsert(
                context,
                PendingMessage(
                    packetId = request.packetId,
                    messageId = request.messageId,
                    chatId = request.chatId,
                    targetNodeId = request.chatId,
                    targetGlobalId = request.peerGlobalId,
                    content = request.message,
                    expiresAt = System.currentTimeMillis() + MESSAGE_GRACE_PERIOD_MS,
                    deliveryStatus = DeliveryStatus.PENDING_SYNC,
                    retryAttempt = 0,
                    nextRetryAt = System.currentTimeMillis(),
                    routeHint = request.peerIp,
                    peerPublicKey = request.peerPublicKey,
                    peerWalletAddress = request.peerWalletAddress,
                    peerDisplayName = request.peerDisplayName
                )
            )
            Log.d("GHALBIT-CHAT-DELIVERY", "id=${request.messageId} state=QUEUED route=PREPARED")
            attemptDelivery(context, keyStore, request, 0, allowRetry = true)
        }
    }

    fun retryPendingForChat(context: Context, chatId: String) {
        bind(context)
        scope.launch {
            flushPending(context.applicationContext, chatId)
            flushPendingMedia(context.applicationContext, chatId)
        }
    }

    fun queueMediaPending(
        context: Context,
        packetId: String,
        messageId: String,
        chatId: String,
        label: String,
        filePath: String,
        mediaType: String,
        mimeType: String,
        fileSize: Long,
        routeHint: String?,
        peerGlobalId: String? = null,
        peerPublicKey: String? = null,
        peerWalletAddress: String? = null,
        peerDisplayName: String? = null,
        waitingForPeer: Boolean = true,
        retryAttempt: Int = 0,
        lastErrorReason: String? = null,
        nextRetryAt: Long = System.currentTimeMillis()
    ) {
        bind(context)
        val state = if (waitingForPeer) ChatDeliveryState.WAITING_FOR_PEER else ChatDeliveryState.PENDING
        updateState(context, packetId, state)
        RuntimeEvidenceCollector.record(
            context,
            RuntimeEvidenceTags.MEDIA_PENDING,
            source = "ChatDeliveryManager",
            messageId = messageId,
            peerId = chatId,
            status = state.name,
            details = lastErrorReason ?: if (waitingForPeer) "peerOffline" else "noRoute"
        )
        PendingMessageStore.upsert(
            context,
            PendingMessage(
                packetId = packetId,
                messageId = messageId,
                chatId = chatId,
                targetNodeId = chatId,
                targetGlobalId = peerGlobalId,
                content = label,
                createdAt = System.currentTimeMillis(),
                expiresAt = System.currentTimeMillis() + MESSAGE_GRACE_PERIOD_MS,
                deliveryStatus = DeliveryStatus.PENDING_SYNC,
                retryAttempt = retryAttempt,
                lastAttemptAt = 0L,
                nextRetryAt = nextRetryAt,
                routeHint = routeHint,
                peerPublicKey = peerPublicKey,
                peerWalletAddress = peerWalletAddress,
                peerDisplayName = peerDisplayName,
                lastFailureReason = lastErrorReason,
                mediaUri = filePath,
                mediaType = mediaType,
                mimeType = mimeType,
                fileSize = fileSize
            )
        )
        Log.d(
            "GHALBIT-MEDIA-PENDING",
            "id=$messageId reason=${lastErrorReason ?: if (waitingForPeer) "peerOffline" else "noRoute"} expiresIn=24h"
        )
    }

    fun retryMessage(context: Context, messageId: String) {
        bind(context)
        scope.launch {
            val pending = PendingMessageStore.all(context).firstOrNull { it.messageId == messageId || it.packetId == messageId } ?: return@launch
            PendingMessageStore.upsert(
                context,
                pending.copy(
                    expiresAt = System.currentTimeMillis() + MESSAGE_GRACE_PERIOD_MS,
                    nextRetryAt = System.currentTimeMillis(),
                    lastFailureReason = "manualRetry"
                )
            )
            updateState(context, pending.packetId, ChatDeliveryState.QUEUED)
            Log.d("GHALBIT-MEDIA-RETRY", "manual=true messageId=$messageId")
            if (!pending.mediaUri.isNullOrBlank()) {
                flushPendingMedia(context, pending.chatId)
            } else {
                flushPending(context, pending.chatId)
            }
        }
    }

    fun editMessage(
        context: Context,
        packetId: String,
        chatId: String,
        newContent: String,
        targetGlobalId: String?
    ) {
        bind(context)
        scope.launch {
            val chatDao = ChatDatabase.getInstance(context).chatDao()
            val message = chatDao.findByPacketId(packetId) ?: return@launch
            if (message.status.uppercase().contains("DELETED")) {
                return@launch
            }
            val state = ChatDeliveryState.fromDb(message.status)
            if (
                state in setOf(
                    ChatDeliveryState.DELIVERED,
                    ChatDeliveryState.DELIVERED_REMOTE,
                    ChatDeliveryState.READ,
                    ChatDeliveryState.READ_REMOTE
                ) && System.currentTimeMillis() - message.timestamp > 15 * 60 * 1000L
            ) {
                Log.d("GHALBIT-MESSAGE-EDIT", "blocked expired window")
                return@launch
            }
            val pending = PendingMessageStore.find(context, packetId)
            if (pending != null) {
                PendingMessageStore.upsert(context, pending.copy(content = newContent))
                chatDao.updateContentAndStatus(packetId, newContent, ChatDeliveryState.EDITING_DRAFT.dbValue)
                Log.d("GHALBIT-MESSAGE-EDIT", "local id=$packetId")
                return@launch
            }
            chatDao.updateContentAndStatus(packetId, newContent, ChatDeliveryState.EDIT_REQUESTED_REMOTE.dbValue)
            if (!targetGlobalId.isNullOrBlank() && OnlineFallbackTransport.isConfigured()) {
                val eventVersion = DraftDatabase.getInstance(context).draftMessageDao().countEditEvent("noop") + 1
                val ok = OnlineFallbackTransport.sendEdit(context, targetGlobalId, packetId, newContent, eventVersion)
                if (ok) {
                    chatDao.updateContentAndStatus(packetId, newContent, ChatDeliveryState.EDITED_REMOTE.dbValue)
                    DraftDatabase.getInstance(context).draftMessageDao().upsertEdit(
                        MessageEditEntity(
                            eventId = "EDIT-$packetId-${System.currentTimeMillis()}",
                            packetId = packetId,
                            chatId = chatId,
                            content = newContent,
                            editVersion = eventVersion,
                            createdAt = System.currentTimeMillis(),
                            senderGlobalId = com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager.localGlobalId(),
                            delivered = true
                        )
                    )
                    Log.d("GHALBIT-MESSAGE-EDIT", "remote requested id=$packetId")
                }
            }
        }
    }

    fun deleteMessageForMe(context: Context, packetId: String) {
        bind(context)
        scope.launch {
            ChatDatabase.getInstance(context).chatDao()
                .updateContentAndStatus(packetId, "Pesan dihapus", ChatDeliveryState.DELETED_LOCAL.dbValue)
            Log.d("GHALBIT-MESSAGE-DELETE", "for_me id=$packetId")
        }
    }

    fun deleteMessageForEveryone(
        context: Context,
        packetId: String,
        chatId: String,
        targetGlobalId: String?
    ) {
        bind(context)
        scope.launch {
            val chatDao = ChatDatabase.getInstance(context).chatDao()
            val message = chatDao.findByPacketId(packetId) ?: return@launch
            if (System.currentTimeMillis() - message.timestamp > 60 * 60 * 1000L) {
                Log.d("GHALBIT-MESSAGE-DELETE", "blocked expired window")
                return@launch
            }
            val pending = PendingMessageStore.find(context, packetId)
            if (pending != null) {
                PendingMessageStore.remove(context, packetId)
                chatDao.updateContentAndStatus(packetId, "Pesan dihapus", ChatDeliveryState.DELETED_REMOTE.dbValue)
                Log.d("GHALBIT-MESSAGE-DELETE", "cancel pending delivery id=$packetId")
                return@launch
            }
            if (!targetGlobalId.isNullOrBlank() && OnlineFallbackTransport.isConfigured()) {
                val ok = OnlineFallbackTransport.sendDelete(context, targetGlobalId, packetId, "DELETE_FOR_EVERYONE")
                if (ok) {
                    chatDao.updateContentAndStatus(packetId, "Pesan dihapus", ChatDeliveryState.DELETE_REQUESTED_REMOTE.dbValue)
                    DraftDatabase.getInstance(context).draftMessageDao().upsertDelete(
                        MessageDeleteEntity(
                            eventId = "DELETE-$packetId-${System.currentTimeMillis()}",
                            packetId = packetId,
                            chatId = chatId,
                            mode = "DELETE_FOR_EVERYONE",
                            createdAt = System.currentTimeMillis(),
                            senderGlobalId = com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager.localGlobalId(),
                            delivered = true
                        )
                    )
                    Log.d("GHALBIT-MESSAGE-DELETE", "for_everyone requested id=$packetId")
                }
            }
        }
    }

    fun flushPending(context: Context, chatId: String? = null) {
        bind(context)
        scope.launch {
            retryMutex.withLock {
                val keyStore = KeyStoreManager(context)
                val now = System.currentTimeMillis()
                val allPending =
                    PendingMessageStore.all(context)
                        .filter { chatId == null || it.chatId == chatId }
                val localOnlineNodes = DiscoveryManager.discoverNodes().count { it.online }
                val relayConfigured = OnlineFallbackTransport.isConfigured()
                val duePending = allPending.count { it.nextRetryAt <= 0L || now >= it.nextRetryAt }
                val skippedPending = allPending.size - duePending
                if (now - lastSummaryLogAt >= LOG_THROTTLE_MS) {
                    lastSummaryLogAt = now
                    Log.d("GHALBIT-PENDING-GUARD", "scan summary total=${allPending.size} due=$duePending skipped=$skippedPending")
                }
                allPending.forEach { pending ->
                        if (pending.deliveryStatus == DeliveryStatus.ACCEPTED_BY_RELAY || pending.deliveryStatus == DeliveryStatus.QUEUED_REMOTE) {
                            return@forEach
                        }
                        if (pending.nextRetryAt > 0L && now < pending.nextRetryAt) {
                            if (shouldLog("skip-not-due", now)) {
                                Log.d("GHALBIT-PENDING-GUARD", "skipped not due")
                            }
                            return@forEach
                        }
                        if (pending.expiresAt > 0L && System.currentTimeMillis() > pending.expiresAt) {
                            updateState(context, pending.packetId, ChatDeliveryState.FAILED_FINAL)
                            PendingMessageStore.upsert(
                                context,
                                pending.copy(
                                    deliveryStatus = DeliveryStatus.EXPIRED_REMOTE,
                                    nextRetryAt = 0L,
                                    lastFailureReason = "expired24h"
                                )
                            )
                            return@forEach
                        }
                        if (!relayConfigured && localOnlineNodes == 0) {
                            val backoff = guardedBackoffMs(pending.retryAttempt)
                            val nextRetryAt = now + backoff
                            updateState(context, pending.packetId, ChatDeliveryState.WAITING_FOR_ROUTE)
                            PendingMessageStore.upsert(
                                context,
                                pending.copy(
                                    retryAttempt = (pending.retryAttempt + 1).coerceAtMost(MAX_RETRY_BUDGET_PER_HOUR),
                                    nextRetryAt = nextRetryAt,
                                    lastAttemptAt = now,
                                    lastFailureReason = "waitingForRoute"
                                )
                            )
                            RuntimeUiStateManager.setTransientState(
                                source = "relay:route-wait",
                                state = RuntimeUiState.OFFLINE_PENDING,
                                title = "Menunggu jalur tersedia",
                                detail = "Belum ada mesh sehat atau relay internet yang siap.",
                                actionsLocked = false
                            )
                            throttledLog("waiting-route", "GHALBIT-PENDING-GUARD", "waiting for route")
                            if (shouldLog("backoff-route", now)) {
                                Log.d("GHALBIT-PENDING-GUARD", "backoff nextRetryAt=$nextRetryAt")
                            }
                            return@forEach
                        }
                        val health = ConversationKeepAliveManager.snapshot(pending.chatId)?.routeHealth
                        if (health == RouteHealthStatus.RECONNECTING || health == RouteHealthStatus.OFFLINE_PENDING) {
                            return@forEach
                        }
                        val request =
                            ChatDeliveryRequest(
                                chatId = pending.chatId,
                                peerIp = pending.routeHint.orEmpty(),
                                message = pending.content,
                                packetId = pending.packetId,
                                messageId = pending.messageId,
                                peerGlobalId = pending.targetGlobalId,
                                peerPublicKey = pending.peerPublicKey,
                                peerWalletAddress = pending.peerWalletAddress,
                                peerDisplayName = pending.peerDisplayName
                            )
                        attemptDelivery(context, keyStore, request, pending.retryAttempt, allowRetry = true)
                }
            }
        }
    }

    fun flushPendingMedia(context: Context, chatId: String? = null) {
        bind(context)
        scope.launch {
            val now = System.currentTimeMillis()
            val keyStore = KeyStoreManager(context)
            PendingMessageStore.mediaItems(context)
                .filter { chatId == null || it.chatId == chatId }
                .filter { it.expiresAt <= 0L || now <= it.expiresAt }
                .filter {
                    val due = it.nextRetryAt <= 0L || now >= it.nextRetryAt
                    if (!due && shouldLog("media-not-due", now)) {
                        Log.d("GHALBIT-PENDING-GUARD", "skipped not due")
                    }
                    due
                }
                .forEach { pending ->
                    if (activeMediaTransfers[pending.chatId] == true) return@forEach
                    attemptMediaDelivery(context, keyStore, pending)
                }
            PendingMessageStore.mediaItems(context)
                .filter { it.expiresAt > 0L && now > it.expiresAt }
                .forEach { expired ->
                    updateState(context, expired.packetId, ChatDeliveryState.FAILED_FINAL)
                    PendingMessageStore.upsert(
                        context,
                        expired.copy(
                            nextRetryAt = 0L,
                            lastFailureReason = "expired24h"
                        )
                    )
                    Log.d("GHALBIT-MEDIA-EXPIRE", "id=${expired.messageId} state=FAILED_FINAL")
                }
        }
    }

    private suspend fun attemptDelivery(
        context: Context,
        keyStore: KeyStoreManager,
        request: ChatDeliveryRequest,
        attempt: Int,
        allowRetry: Boolean
    ) {
        val routeHealth = ConversationKeepAliveManager.snapshot(request.chatId)?.routeHealth
        if ((routeHealth == RouteHealthStatus.RECONNECTING || routeHealth == RouteHealthStatus.OFFLINE_PENDING) && !OnlineFallbackTransport.isConfigured()) {
            val nextRetryAt = System.currentTimeMillis() + guardedBackoffMs(attempt)
            schedulePendingState(
                context = context,
                request = request,
                attempt = attempt,
                state = ChatDeliveryState.WAITING_FOR_ROUTE,
                nextRetryAt = nextRetryAt,
                reason = "routePending"
            )
            throttledLog("waiting-route", "GHALBIT-PENDING-GUARD", "waiting for route")
            return
        }

        activeAttempts[request.packetId] = attempt + 1
        updateState(context, request.packetId, ChatDeliveryState.SENDING)

        val result =
            ChatSendHelper.sendTextMessage(
                context = context,
                keyStore = keyStore,
                peerName = request.chatId,
                peerIp = request.peerIp,
                message = request.message,
                packetId = request.packetId,
                peerGlobalId = request.peerGlobalId,
                peerPublicKey = request.peerPublicKey,
                peerWalletAddress = request.peerWalletAddress,
                peerDisplayName = request.peerDisplayName
            )

        val state =
            when (result.deliveryStatus) {
                DeliveryStatus.LOCAL_SENT -> ChatDeliveryState.SENT_LOCAL
                DeliveryStatus.INTERNET_SENT -> ChatDeliveryState.SENT_INTERNET
                DeliveryStatus.ACCEPTED_BY_RELAY -> ChatDeliveryState.ACCEPTED_BY_RELAY
                DeliveryStatus.QUEUED_REMOTE -> ChatDeliveryState.QUEUED_REMOTE
                DeliveryStatus.MEDIA_UPLOADING -> ChatDeliveryState.MEDIA_UPLOADING
                DeliveryStatus.MEDIA_RESUMING -> ChatDeliveryState.MEDIA_RESUMING
                DeliveryStatus.MEDIA_QUEUED_REMOTE -> ChatDeliveryState.MEDIA_QUEUED_REMOTE
                DeliveryStatus.MEDIA_DELIVERED_REMOTE -> ChatDeliveryState.MEDIA_DELIVERED_REMOTE
                DeliveryStatus.MEDIA_READ_REMOTE -> ChatDeliveryState.MEDIA_READ_REMOTE
                DeliveryStatus.MEDIA_EXPIRED -> ChatDeliveryState.MEDIA_EXPIRED
                DeliveryStatus.INTERNET_RELAY_NOT_CONFIGURED -> ChatDeliveryState.RELAY_CONFIG_REQUIRED
                DeliveryStatus.PENDING_SYNC -> ChatDeliveryState.PENDING
                DeliveryStatus.FAILED -> ChatDeliveryState.FAILED_RETRYING
                DeliveryStatus.DELIVERED_REMOTE -> ChatDeliveryState.DELIVERED_REMOTE
                DeliveryStatus.READ_REMOTE -> ChatDeliveryState.READ_REMOTE
                DeliveryStatus.EXPIRED_REMOTE -> ChatDeliveryState.EXPIRED_REMOTE
            }
        updateState(context, request.packetId, state)
        Log.d("GHALBIT-CHAT-DELIVERY", "id=${request.messageId} state=${state.dbValue} route=${result.routeLabel}")

        if (result.deliveryStatus == DeliveryStatus.LOCAL_SENT) {
            PendingMessageStore.remove(context, request.packetId)
            activeAttempts.remove(request.packetId)
            return
        }

        if (result.deliveryStatus == DeliveryStatus.ACCEPTED_BY_RELAY || result.deliveryStatus == DeliveryStatus.QUEUED_REMOTE) {
            PendingMessageStore.upsert(
                context,
                PendingMessageStore.find(context, request.packetId)?.copy(
                    deliveryStatus = result.deliveryStatus,
                    expiresAt = result.expiresAt.takeIf { it > 0L } ?: (System.currentTimeMillis() + MESSAGE_GRACE_PERIOD_MS),
                    lastAttemptAt = System.currentTimeMillis(),
                    nextRetryAt = System.currentTimeMillis() + 15 * 60 * 1000L,
                    lastFailureReason = null
                ) ?: PendingMessage(
                    packetId = request.packetId,
                    messageId = request.messageId,
                    chatId = request.chatId,
                    targetNodeId = request.chatId,
                    targetGlobalId = request.peerGlobalId,
                    content = request.message,
                    createdAt = System.currentTimeMillis(),
                    expiresAt = result.expiresAt.takeIf { it > 0L } ?: (System.currentTimeMillis() + MESSAGE_GRACE_PERIOD_MS),
                    deliveryStatus = result.deliveryStatus,
                    retryAttempt = attempt,
                    lastAttemptAt = System.currentTimeMillis(),
                    nextRetryAt = System.currentTimeMillis() + 15 * 60 * 1000L,
                    routeHint = request.peerIp,
                    peerPublicKey = request.peerPublicKey,
                    peerWalletAddress = request.peerWalletAddress,
                    peerDisplayName = request.peerDisplayName
                )
            )
            Log.d(
                "GHALBIT-ANDROID-RELAY",
                if (result.deliveryStatus == DeliveryStatus.ACCEPTED_BY_RELAY) {
                    "accepted messageId=${request.messageId}"
                } else {
                    "queued remote messageId=${request.messageId}"
                }
            )
            activeAttempts.remove(request.packetId)
            return
        }

        if (result.deliveryStatus == DeliveryStatus.INTERNET_RELAY_NOT_CONFIGURED) {
            val nextRetryAt = System.currentTimeMillis() + guardedBackoffMs(attempt + 1)
            schedulePendingState(
                context = context,
                request = request,
                attempt = attempt + 1,
                state = ChatDeliveryState.RELAY_CONFIG_REQUIRED,
                nextRetryAt = nextRetryAt,
                reason = "relayConfigMissing"
            )
            RuntimeUiStateManager.setTransientState(
                source = "relay:missing-config",
                state = RuntimeUiState.OFFLINE_PENDING,
                title = "Relay belum dikonfigurasi",
                detail = "Mode internet ditahan sampai URL relay tersedia.",
                actionsLocked = false
            )
            throttledLog("relay-missing", "GHALBIT-PENDING-GUARD", "relay config missing, retry paused")
            if (shouldLog("relay-backoff", System.currentTimeMillis())) {
                Log.d("GHALBIT-PENDING-GUARD", "backoff nextRetryAt=$nextRetryAt")
            }
            activeAttempts.remove(request.packetId)
            return
        }

        if (!allowRetry) {
            val nextRetryAt = System.currentTimeMillis() + guardedBackoffMs(attempt + 1)
            schedulePendingState(
                context = context,
                request = request,
                attempt = attempt + 1,
                state = ChatDeliveryState.FAILED_RETRYING,
                nextRetryAt = nextRetryAt,
                reason = "ttlGuardPending"
            )
            updateState(context, request.packetId, ChatDeliveryState.FAILED_RETRYING)
            RuntimeEvidenceCollector.record(
                context,
                RuntimeEvidenceTags.FAILED_BEFORE_TTL_BLOCKED,
                source = "ChatDeliveryManager",
                messageId = request.messageId,
                peerId = request.chatId,
                status = "BLOCKED_TO_PENDING",
                details = "ttlGuardPending"
            )
            Log.d("GHALBIT-CHAT-RETRY", "messageId=${request.messageId} ttlGuard pending=true")
            return
        }

        if (attempt >= retryBackoffMs.lastIndex) {
            PendingMessageStore.upsert(
                context,
                PendingMessageStore.find(context, request.packetId)?.copy(
                    deliveryStatus = DeliveryStatus.PENDING_SYNC,
                    retryAttempt = attempt + 1,
                    lastAttemptAt = System.currentTimeMillis(),
                    lastFailureReason = "retryExhausted"
                ) ?: PendingMessage(
                    packetId = request.packetId,
                    messageId = request.messageId,
                    chatId = request.chatId,
                    targetNodeId = request.chatId,
                    targetGlobalId = request.peerGlobalId,
                    content = request.message,
                    deliveryStatus = DeliveryStatus.PENDING_SYNC,
                    retryAttempt = attempt + 1,
                    routeHint = request.peerIp,
                    peerPublicKey = request.peerPublicKey,
                    peerWalletAddress = request.peerWalletAddress,
                    peerDisplayName = request.peerDisplayName,
                    lastFailureReason = "retryExhausted"
                )
            )
            updateState(context, request.packetId, ChatDeliveryState.PENDING)
            RuntimeEvidenceCollector.record(
                context,
                RuntimeEvidenceTags.MESSAGE_PENDING,
                source = "ChatDeliveryManager",
                messageId = request.messageId,
                peerId = request.chatId,
                status = "PENDING",
                details = "noRoute"
            )
            Log.d("GHALBIT-CHAT-PENDING", "id=${request.messageId} reason=noRoute")
            return
        }

        val nextAttempt = attempt + 1
        val backoff = guardedBackoffMs(attempt)
        val nextRetryAt = System.currentTimeMillis() + backoff
        schedulePendingState(
            context = context,
            request = request,
            attempt = nextAttempt,
            state = ChatDeliveryState.FAILED_RETRYING,
            nextRetryAt = nextRetryAt,
            reason = "retryScheduled"
        )
        updateState(context, request.packetId, ChatDeliveryState.FAILED_RETRYING)
        Log.d("GHALBIT-CHAT-RETRY", "messageId=${request.messageId} attempt=$nextAttempt")
        Log.d("GHALBIT-PENDING-GUARD", "backoff nextRetryAt=$nextRetryAt")
    }

    fun handleAck(context: Context, packetId: String) {
        updateState(context, packetId, ChatDeliveryState.DELIVERED)
        PendingMessageStore.remove(context, packetId)
        RuntimeEvidenceCollector.record(
            context,
            RuntimeEvidenceTags.MESSAGE_DELIVERED,
            source = "ChatDeliveryManager",
            messageId = packetId,
            status = "DELIVERED"
        )
        Log.d("GHALBIT-CHAT-ACK", "id=$packetId delivered=true")
    }

    fun handleRead(context: Context, packetId: String) {
        updateState(context, packetId, ChatDeliveryState.READ)
        PendingMessageStore.remove(context, packetId)
        RuntimeEvidenceCollector.record(
            context,
            RuntimeEvidenceTags.MESSAGE_READ,
            source = "ChatDeliveryManager",
            messageId = packetId,
            status = "READ"
        )
        Log.d("GHALBIT-CHAT-ACK", "id=$packetId read=true")
    }

    fun handleIncomingMessage(
        context: Context,
        packet: MeshPacket,
        payload: String,
        peerIp: String,
        onNewMessage: suspend () -> Unit
    ) {
        scope.launch {
            recentInboundIds[packet.packetId] = System.currentTimeMillis()
            if (recentInboundIds.size > MAX_RECENT_IDS) {
                val oldest = recentInboundIds.entries.minByOrNull { it.value }?.key
                if (oldest != null) {
                    recentInboundIds.remove(oldest)
                }
            }
            val chatDb = ChatDatabase.getInstance(context)
            if (chatDb.chatDao().countByPacketId(packet.packetId) > 0) {
                Log.d("GHALBIT-CHAT-DEDUP", "duplicate ignored messageId=${packet.packetId}")
                sendDeliveredReceipt(context, packet, peerIp)
                return@launch
            }
            onNewMessage()
            sendDeliveredReceipt(context, packet, peerIp)
        }
    }

    private fun sendDeliveredReceipt(context: Context, packet: MeshPacket, peerIp: String) {
        if (peerIp.isBlank()) return
        runCatching {
            val receipt =
                MeshPacket(
                    packetId = "CHAT-DELIVERED-${System.currentTimeMillis()}",
                    source = MainActivity.myGlobalPeerId,
                    destination = packet.source,
                    type = "CHAT_DELIVERED",
                    payload = packet.packetId,
                    encrypted = false
                )
            MeshSocketClient.send(peerIp, receipt)
        }
    }

    private fun upsertOutgoingMessage(
        context: Context,
        request: ChatDeliveryRequest,
        state: ChatDeliveryState
    ) {
        val chatDb = ChatDatabase.getInstance(context)
        if (chatDb.chatDao().countByPacketId(request.packetId) == 0) {
            chatDb.chatDao().insertMessage(
                ChatMessage(
                    packetId = request.packetId,
                    chatId = request.chatId,
                    senderName = "ME",
                    content = request.message,
                    isSent = true,
                    status = state.dbValue
                )
            )
        } else {
            chatDb.chatDao().updateStatus(request.packetId, state.dbValue)
        }
    }

    private fun updateState(context: Context, packetId: String, state: ChatDeliveryState) {
        val dao = ChatDatabase.getInstance(context).chatDao()
        val previous = dao.findByPacketId(packetId)?.status.orEmpty()
        dao.updateStatus(packetId, state.dbValue)
        if (previous != state.dbValue) {
            val semantic = DeliverySemanticStage.fromState(state)
            Log.d(
                "GHALBIT-DELIVERY-SEMANTIC",
                "packetId=$packetId stage=${semantic.name} state=${state.dbValue}"
            )
        }
    }

    private suspend fun syncInternetInbox(context: Context): RelayInboxResult {
        if (!OnlineFallbackTransport.isConfigured()) {
            return RelayInboxResult(emptyList(), emptyList(), emptyList(), emptyList(), "missing_config")
        }
        if (!OnlinePresenceManager.hasInternet(context)) {
            Log.w("GHALBIT-RESILIENCE", "no internet")
            return RelayInboxResult(emptyList(), emptyList(), emptyList(), emptyList(), "no_internet")
        }
        val resolvedGlobalId = com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager.localGlobalId()
        if (resolvedGlobalId.isBlank()) {
            return RelayInboxResult(emptyList(), emptyList(), emptyList(), emptyList(), "missing_global_id")
        }
        val inbox = OnlineFallbackTransport.fetchInbox(context, resolvedGlobalId)
        inbox.messages.forEach { handleRemoteRelayMessage(context, it) }
        inbox.receipts.forEach { handleRemoteRelayReceipt(context, it) }
        val edits = OnlineFallbackTransport.fetchEdits(context, resolvedGlobalId)
        edits.forEach { handleRemoteRelayEdit(context, it) }
        val deletes = OnlineFallbackTransport.fetchDeletes(context, resolvedGlobalId)
        deletes.forEach { handleRemoteRelayDelete(context, it) }
        return inbox.copy(edits = edits, deletes = deletes)
    }

    private suspend fun handleRemoteRelayMessage(context: Context, message: RelayInboxMessage) {
        val callSignalType = resolveRelayCallSignalType(message)
        if (callSignalType != null) {
            val signalPayload = unwrapRelaySignalPayload(message.payload)
            val sourceNode = message.senderNodeId.ifBlank { message.senderGlobalId }
            Log.d("GHALBIT-CALL-INBOX", "received type=$callSignalType callId=${CallManager.extractCallId(signalPayload)} source=$sourceNode")
            when (callSignalType) {
                CallManager.SIGNAL_CALL_WEBRTC_OFFER -> Log.d("GHALBIT-CALL-OFFER", "received source=$sourceNode")
                CallManager.SIGNAL_CALL_WEBRTC_ANSWER -> Log.d("GHALBIT-CALL-ANSWER", "received source=$sourceNode")
                CallManager.SIGNAL_CALL_WEBRTC_ICE -> Log.d("GHALBIT-CALL-ICE", "received source=$sourceNode")
            }
            LocalBroadcastManager.getInstance(context).sendBroadcast(
                Intent("com.ghalbitnet.meshx2.NEW_MESH_PACKET")
                    .putExtra("packetId", message.packetId)
                    .putExtra("source", sourceNode)
                    .putExtra("destination", com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager.localNodeId())
                    .putExtra("payload", signalPayload)
                    .putExtra("type", callSignalType)
                    .putExtra("encrypted", false)
            )
            return
        }
        val chatDb = ChatDatabase.getInstance(context)
        val chatId = message.senderDisplayName?.takeIf { it.isNotBlank() } ?: message.senderGlobalId
        val resolvedPublicKeyHash = message.senderPublicKeyHash ?: message.senderPublicKey?.let { com.ghalbitnet.meshx2.call.CallManager.publicKeyHash(it) }
        ConversationIdentityStore.upsert(
            context = context,
            chatId = chatId,
            metadata = ConversationIdentityMetadata(
                chatId = chatId,
                globalId = message.senderGlobalId,
                publicKey = message.senderPublicKey,
                publicKeyHash = resolvedPublicKeyHash,
                canonicalDisplayName = message.senderDisplayName ?: chatId,
                verificationStatus = PeerVerificationStatus.VERIFIED
            )
        )
        if (chatDb.chatDao().countByPacketId(message.packetId) == 0) {
            val resolvedContent =
                when (message.contentType.uppercase()) {
                    "ENCRYPTED_TEXT" -> decryptRemotePayload(context, message)
                    else -> message.payload
                }
            if (message.contentType.equals("SOS", ignoreCase = true)) {
                routeRelaySosToAlertManager(
                    context = context,
                    message = message,
                    payload = resolvedContent
                )
            }
            val internalEvent =
                InternalEventRouter.toChatMessage(
                    context = context,
                    packetId = message.packetId,
                    chatId = chatId,
                    senderName = message.senderDisplayName ?: message.senderGlobalId,
                    type = message.contentType,
                    payload = resolvedContent,
                    isSent = false,
                    status = if (message.contentType.uppercase() == "MEDIA") ChatDeliveryState.MEDIA_DELIVERED_REMOTE.dbValue else ChatDeliveryState.DELIVERED_REMOTE.dbValue,
                    senderGlobalId = message.senderGlobalId,
                    publicDisplayName = message.senderDisplayName,
                    publicNickname = message.senderDisplayName
                )
            val nextMessage =
                internalEvent ?: ChatMessage(
                    packetId = message.packetId,
                    chatId = chatId,
                    senderName = message.senderDisplayName ?: message.senderGlobalId,
                    content = resolvedContent,
                    contentType = message.contentType,
                    filePath = if (message.contentType.uppercase() == "MEDIA") extractRemoteMediaUrl(message) else null,
                    isSent = false,
                    status = if (message.contentType.uppercase() == "MEDIA") ChatDeliveryState.MEDIA_DELIVERED_REMOTE.dbValue else ChatDeliveryState.DELIVERED_REMOTE.dbValue
                )
            chatDb.chatDao().insertMessage(nextMessage)
            if (!ChatActivity.isViewingChatWith(chatId) && nextMessage.visibilityType != MessageVisibility.HIDDEN.name) {
                AppNotificationManager.notifyChatMessage(
                    context = context,
                    peerName = chatId,
                    message = nextMessage.content.take(160),
                    peerGlobalId = message.senderGlobalId,
                    peerPublicKey = message.senderPublicKey,
                    peerDisplayName = message.senderDisplayName ?: chatId,
                    messageId = message.messageId
                )
            }
            Log.d("GHALBIT-BG", "message received id=${message.messageId}")
        }
        val localGlobalId = com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager.localGlobalId()
        OnlineFallbackTransport.sendAck(context, localGlobalId, message.senderGlobalId, message.messageId)
        Log.d("GHALBIT-CHAT-ACK", "id=${message.messageId} delivered=true remote=true")
    }

    private fun routeRelaySosToAlertManager(
        context: Context,
        message: RelayInboxMessage,
        payload: String
    ) {
        val sourceNode = message.senderNodeId.ifBlank { message.senderGlobalId }
        Log.d(
            "GHALBIT-SOS-INBOX",
            "received packetId=${message.packetId} source=$sourceNode globalId=${message.senderGlobalId}"
        )
        SosAlertManager.handleIncomingSos(
            context = context,
            packet = MeshPacket(
                packetId = message.packetId.ifBlank { message.messageId },
                source = sourceNode,
                destination = com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager.localNodeId(),
                type = "SOS",
                payload = payload,
                encrypted = false
            ),
            payload = payload,
            routeHint = "relay:${message.senderGlobalId}"
        )
    }

    private fun resolveRelayCallSignalType(message: RelayInboxMessage): String? {
        val contentType = message.contentType.uppercase()
        if (contentType.startsWith("CALL_")) return contentType
        val payloadJson = runCatching { JSONObject(message.payload) }.getOrNull() ?: return null
        val outerType = payloadJson.optString("type")
        val signalType = payloadJson.optString("signalType")
        return when {
            outerType.startsWith("CALL_") -> outerType
            signalType.startsWith("CALL_") -> signalType
            else -> null
        }
    }

    private fun unwrapRelaySignalPayload(rawPayload: String): String {
        val json = runCatching { JSONObject(rawPayload) }.getOrNull() ?: return rawPayload
        val wrappedPayload = json.optString("payload")
        return if (wrappedPayload.isNotBlank()) wrappedPayload else rawPayload
    }

    private fun handleRemoteRelayReceipt(context: Context, receipt: RelayInboxReceipt) {
        val packetKey = receipt.packetId.ifBlank { receipt.messageId }
        val isMedia = ChatDatabase.getInstance(context).chatDao().findByPacketId(packetKey)?.contentType?.uppercase() == "MEDIA"
        when (receipt.type.uppercase()) {
            "DELIVERED_REMOTE" -> {
                updateState(context, packetKey, if (isMedia) ChatDeliveryState.MEDIA_DELIVERED_REMOTE else ChatDeliveryState.DELIVERED_REMOTE)
                PendingMessageStore.remove(context, packetKey)
                Log.d("GHALBIT-CHAT-ACK", "id=${receipt.messageId} delivered=true")
                Log.d("GHALBIT-BG", "receipt received id=${receipt.messageId}")
            }
            "READ_REMOTE" -> {
                updateState(context, packetKey, if (isMedia) ChatDeliveryState.MEDIA_READ_REMOTE else ChatDeliveryState.READ_REMOTE)
                PendingMessageStore.remove(context, packetKey)
                Log.d("GHALBIT-CHAT-ACK", "id=${receipt.messageId} read=true")
                Log.d("GHALBIT-READ", "remote receipt applied id=${receipt.messageId}")
                Log.d("GHALBIT-BG", "receipt received id=${receipt.messageId}")
            }
            "EXPIRED_REMOTE" -> {
                updateState(context, packetKey, if (isMedia) ChatDeliveryState.MEDIA_EXPIRED else ChatDeliveryState.FAILED_FINAL)
                PendingMessageStore.remove(context, packetKey)
                Log.d("GHALBIT-MEDIA-EXPIRE", "id=${receipt.messageId} state=FAILED_FINAL")
            }
        }
    }

    private fun handleRemoteRelayEdit(context: Context, edit: RelayInboxEdit) {
        val draftDao = DraftDatabase.getInstance(context).draftMessageDao()
        if (draftDao.countEditEvent(edit.eventId) > 0) {
            Log.d("GHALBIT-CHAT-EVENT", "duplicate skipped id=${edit.eventId}")
            return
        }
        ChatDatabase.getInstance(context).chatDao()
            .updateContentAndStatus(edit.packetId, edit.content, ChatDeliveryState.EDITED_REMOTE.dbValue)
        draftDao.upsertEdit(
            MessageEditEntity(
                eventId = edit.eventId,
                packetId = edit.packetId,
                chatId = edit.senderGlobalId,
                content = edit.content,
                editVersion = edit.editVersion,
                createdAt = edit.editedAt,
                senderGlobalId = edit.senderGlobalId,
                delivered = true
            )
        )
        Log.d("GHALBIT-MESSAGE-EDIT", "applied id=${edit.originalMessageId}")
    }

    private fun handleRemoteRelayDelete(context: Context, delete: RelayInboxDelete) {
        val draftDao = DraftDatabase.getInstance(context).draftMessageDao()
        if (draftDao.countDeleteEvent(delete.eventId) > 0) {
            Log.d("GHALBIT-CHAT-EVENT", "duplicate skipped id=${delete.eventId}")
            return
        }
        ChatDatabase.getInstance(context).chatDao()
            .updateContentAndStatus(delete.packetId, "Pesan dihapus", ChatDeliveryState.DELETED_REMOTE.dbValue)
        draftDao.upsertDelete(
            MessageDeleteEntity(
                eventId = delete.eventId,
                packetId = delete.packetId,
                chatId = delete.senderGlobalId,
                mode = delete.mode,
                createdAt = delete.deletedAt,
                senderGlobalId = delete.senderGlobalId,
                delivered = true
            )
        )
        Log.d("GHALBIT-MESSAGE-DELETE", "applied remote id=${delete.originalMessageId}")
    }

    fun markChatReadRemotely(context: Context, chatId: String, targetGlobalId: String?) {
        if (targetGlobalId.isNullOrBlank() || !OnlineFallbackTransport.isConfigured()) return
        bind(context)
        scope.launch {
            val unread = ChatDatabase.getInstance(context).chatDao().getUnreadIncoming(chatId)
            if (unread.isEmpty()) return@launch
            val localGlobalId = com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager.localGlobalId()
            unread.forEach { message ->
                OnlineFallbackTransport.sendRead(context, localGlobalId, targetGlobalId, message.packetId)
                updateState(context, message.packetId, ChatDeliveryState.READ_REMOTE)
                Log.d("GHALBIT-READ", "remote receipt sent id=${message.packetId}")
            }
        }
    }

    private suspend fun attemptMediaDelivery(
        context: Context,
        keyStore: KeyStoreManager,
        pending: PendingMessage
    ) {
        val mediaPath = pending.mediaUri ?: return
        val file = java.io.File(mediaPath)
        if (!file.exists()) {
            updateState(context, pending.packetId, ChatDeliveryState.FAILED_FINAL)
            PendingMessageStore.upsert(context, pending.copy(lastFailureReason = "fileMissing", nextRetryAt = 0L))
            Log.d("GHALBIT-MEDIA-EXPIRE", "id=${pending.messageId} state=FAILED_FINAL")
            return
        }
        val routeHealth = ConversationKeepAliveManager.snapshot(pending.chatId)?.routeHealth
        val hasOnlineRelay = OnlinePresenceManager.getOnlineRoute(context, pending.targetGlobalId.orEmpty()) != null
        if (routeHealth == RouteHealthStatus.OFFLINE_PENDING || (pending.routeHint.isNullOrBlank() && !hasOnlineRelay)) {
            val nextRetryAt = System.currentTimeMillis() + guardedBackoffMs(pending.retryAttempt)
            updateState(context, pending.packetId, ChatDeliveryState.PENDING)
            PendingMessageStore.upsert(
                context,
                pending.copy(
                    deliveryStatus = DeliveryStatus.PENDING_SYNC,
                    expiresAt = maxOf(pending.expiresAt, System.currentTimeMillis() + MESSAGE_GRACE_PERIOD_MS),
                    retryAttempt = (pending.retryAttempt + 1).coerceAtMost(MAX_RETRY_BUDGET_PER_HOUR),
                    nextRetryAt = nextRetryAt,
                    lastAttemptAt = System.currentTimeMillis(),
                    lastFailureReason = "peerOffline"
                )
            )
            Log.d("GHALBIT-MEDIA-PENDING", "id=${pending.messageId} ttl=${maxOf(pending.expiresAt - System.currentTimeMillis(), MESSAGE_GRACE_PERIOD_MS)}")
            throttledLog("waiting-route", "GHALBIT-PENDING-GUARD", "waiting for route")
            return
        }
        if (hasOnlineRelay && (pending.routeHint.isNullOrBlank() || routeHealth == RouteHealthStatus.INTERNET_FALLBACK)) {
            updateState(
                context,
                pending.packetId,
                if (pending.uploadSessionId.isNullOrBlank()) ChatDeliveryState.MEDIA_UPLOADING else ChatDeliveryState.MEDIA_RESUMING
            )
            val remoteResult = RemoteMediaRelayManager.uploadPendingMedia(context, pending)
            if (remoteResult.successful) {
                PendingMessageStore.upsert(
                    context,
                    pending.copy(
                        deliveryStatus = DeliveryStatus.MEDIA_QUEUED_REMOTE,
                        remoteMediaId = remoteResult.mediaId,
                        secureMediaToken = remoteResult.secureMediaToken,
                        uploadState = remoteResult.status,
                        expiresAt = remoteResult.expiresAt.takeIf { it > 0L } ?: pending.expiresAt
                    )
                )
                updateState(context, pending.packetId, ChatDeliveryState.MEDIA_QUEUED_REMOTE)
                Log.d("GHALBIT-MEDIA", "remote queued mediaId=${remoteResult.mediaId}")
                return
            }
            if (remoteResult.status == "MEDIA_RESUMING") {
                updateState(context, pending.packetId, ChatDeliveryState.MEDIA_RESUMING)
                PendingMessageStore.upsert(
                    context,
                    pending.copy(
                        uploadState = "MEDIA_RESUMING",
                        lastFailureReason = remoteResult.error ?: "chunk_failed"
                    )
                )
                Log.d("GHALBIT-MEDIA", "resume upload mediaId=${remoteResult.mediaId}")
                return
            }
        }
        activeMediaTransfers[pending.chatId] = true
        updateState(context, pending.packetId, ChatDeliveryState.SENDING)
        val result = CompletableDeferred<Pair<Boolean, String>>()
        Log.d("GHALBIT-MEDIA-SEND", "id=${pending.messageId} state=SENDING")
        FileTransferManager.sendFile(
            context = context,
            fileUri = Uri.fromFile(file),
            destinationPeerId = pending.chatId,
            keyStore = keyStore,
            myPeerId = MainActivity.myGlobalPeerId,
            listener =
                object : FileTransferManager.TransferStatusListener {
                    override fun onProgress(message: String, busy: Boolean) = Unit

                    override fun onComplete(message: String) {
                        if (!result.isCompleted) result.complete(true to message)
                    }

                    override fun onError(message: String) {
                        if (!result.isCompleted) result.complete(false to message)
                    }
                }
        )
        val (success, message) = result.await()
        activeMediaTransfers.remove(pending.chatId)
        if (success) {
            updateState(context, pending.packetId, ChatDeliveryState.SENT_LOCAL)
            PendingMessageStore.remove(context, pending.packetId)
            Log.d("GHALBIT-MEDIA-SEND", "id=${pending.messageId} state=SENT_LOCAL")
            Log.d("GHALBIT-MEDIA-ACK", "id=${pending.messageId} delivered=true")
            return
        }
        val nextAttempt = pending.retryAttempt + 1
        val delayMs = mediaRetryBackoffMs.getOrElse(pending.retryAttempt) { mediaRetryBackoffMs.last() }
        val waitingForPeer = message.contains("Alamat tujuan", ignoreCase = true) || message.contains("belum tersedia", ignoreCase = true)
        val finalState = if (waitingForPeer) ChatDeliveryState.WAITING_FOR_PEER else ChatDeliveryState.FAILED_RETRYING
        updateState(context, pending.packetId, finalState)
        PendingMessageStore.upsert(
            context,
            pending.copy(
                retryAttempt = nextAttempt,
                lastAttemptAt = System.currentTimeMillis(),
                nextRetryAt = System.currentTimeMillis() + delayMs,
                lastFailureReason = message
            )
        )
        Log.d("GHALBIT-MEDIA-RETRY", "id=${pending.messageId} attempt=$nextAttempt route=${if (hasOnlineRelay) "INTERNET_RELAY" else "LOCAL_MESH"}")
    }

    private fun schedulePendingState(
        context: Context,
        request: ChatDeliveryRequest,
        attempt: Int,
        state: ChatDeliveryState,
        nextRetryAt: Long,
        reason: String
    ) {
        PendingMessageStore.upsert(
            context,
            PendingMessageStore.find(context, request.packetId)?.copy(
                deliveryStatus = DeliveryStatus.PENDING_SYNC,
                retryAttempt = attempt.coerceAtMost(MAX_RETRY_BUDGET_PER_HOUR),
                lastAttemptAt = System.currentTimeMillis(),
                nextRetryAt = nextRetryAt,
                lastFailureReason = reason
            ) ?: PendingMessage(
                packetId = request.packetId,
                messageId = request.messageId,
                chatId = request.chatId,
                targetNodeId = request.chatId,
                targetGlobalId = request.peerGlobalId,
                content = request.message,
                deliveryStatus = DeliveryStatus.PENDING_SYNC,
                retryAttempt = attempt.coerceAtMost(MAX_RETRY_BUDGET_PER_HOUR),
                lastAttemptAt = System.currentTimeMillis(),
                nextRetryAt = nextRetryAt,
                routeHint = request.peerIp,
                peerPublicKey = request.peerPublicKey,
                peerWalletAddress = request.peerWalletAddress,
                peerDisplayName = request.peerDisplayName,
                lastFailureReason = reason
            )
        )
        updateState(context, request.packetId, state)
    }

    private fun guardedBackoffMs(attempt: Int): Long {
        return retryBackoffMs.getOrElse(attempt.coerceAtLeast(0)) { retryBackoffMs.last() }
    }

    private fun shouldLog(key: String, now: Long): Boolean {
        return when (key) {
            "relay-missing" -> now - lastRelayMissingLogAt >= LOG_THROTTLE_MS
            "waiting-route" -> now - lastWaitingRouteLogAt >= LOG_THROTTLE_MS
            "single-worker" -> now - lastSingleWorkerLogAt >= LOG_THROTTLE_MS
            else -> true
        }
    }

    private fun throttledLog(key: String, tag: String, message: String) {
        val now = System.currentTimeMillis()
        if (!shouldLog(key, now)) {
            Log.d("GHALBIT-PENDING-GUARD", "log throttled")
            return
        }
        when (key) {
            "relay-missing" -> lastRelayMissingLogAt = now
            "waiting-route" -> lastWaitingRouteLogAt = now
            "single-worker" -> lastSingleWorkerLogAt = now
        }
        Log.d(tag, message)
    }

    fun dryRun(context: Context): ChatDeliveryDryRunReport {
        bind(context)
        val hasPending = PendingMessageStore.all(context).isNotEmpty()
        val relayConfigured = OnlineFallbackTransport.isConfigured()
        val mediaPendingCount = PendingMessageStore.mediaItems(context).size
        val expiredMediaCount = PendingMessageStore.mediaItems(context).count { it.expiresAt > 0L && System.currentTimeMillis() > it.expiresAt }
        val nextRetry = PendingMessageStore.mediaItems(context).map { it.nextRetryAt }.filter { it > 0L }.minOrNull()
        val localRoute = AdaptiveRouteManager.evaluate(context, "dry-run-chat", null, null)
        val summary =
            "engine=true relayConfigured=$relayConfigured realtime=${RelayRealtimeChannel.isConnected()} local=${localRoute.routeType.name} pending=${hasPending} mediaPending=$mediaPendingCount mediaExpired=$expiredMediaCount nextRetry=${nextRetry ?: 0L} dedupCache=${recentInboundIds.size} retry=${retryBackoffMs.joinToString("/")}"
        val report =
            ChatDeliveryDryRunReport(
                engineReady = relayConfigured,
                pendingQueueReady = true,
                dedupReady = true,
                retryReady = true,
                routeSwitchReady = true,
                summary = summary
            )
        _lastDryRun.value = report
        Log.d("GHALBIT-CHAT-DELIVERY", "dryrun $summary")
        return report
    }

    fun createRequest(
        context: Context,
        keyStore: KeyStoreManager,
        chatId: String,
        peerIp: String,
        message: String,
        peerGlobalId: String? = null,
        peerPublicKey: String? = null,
        peerWalletAddress: String? = null,
        peerDisplayName: String? = null
    ): ChatDeliveryRequest {
        val packetId = "CHAT-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
        val messageId = "MSG-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
        val resolved =
            CentralIdentityResolver.resolve(
                context = context,
                legacyChatId = chatId,
                peerName = chatId,
                peerIp = peerIp,
                globalIdHint = peerGlobalId,
                publicKeyHint = peerPublicKey,
                walletAddressHint = peerWalletAddress,
                displayNameHint = peerDisplayName,
                reinforce = false
            )
        return ChatDeliveryRequest(
            chatId = chatId,
            peerIp = peerIp,
            message = message,
            packetId = packetId,
            messageId = messageId,
            peerGlobalId = resolved.globalId?.takeIf { it.isNotBlank() } ?: peerGlobalId,
            peerPublicKey = resolved.publicKey?.takeIf { it.isNotBlank() } ?: peerPublicKey,
            peerWalletAddress = resolved.walletAddress?.takeIf { it.isNotBlank() } ?: peerWalletAddress,
            peerDisplayName = resolved.displayName?.takeIf { it.isNotBlank() } ?: peerDisplayName
        )
    }

    private fun decryptRemotePayload(context: Context, message: RelayInboxMessage): String {
        val senderKey = message.senderPublicKey ?: return message.payload
        return runCatching {
            val keyStore = KeyStoreManager(context)
            val sharedSecret = CryptoEngine.deriveSharedSecret(keyStore.privateKey, CryptoEngine.base64ToPublicKey(senderKey))
            val plainBytes = CryptoEngine.decrypt(Base64.decode(message.payload, Base64.NO_WRAP), sharedSecret)
            String(plainBytes)
        }.getOrElse {
            Log.w("GHALBIT-SECURITY", "encrypted payload fallback ${it.message}")
            message.payload
        }
    }

    private fun extractRemoteMediaUrl(message: RelayInboxMessage): String? {
        return runCatching {
            val json = JSONObject(message.payload)
            val mediaId = json.optString("mediaId")
            val token = json.optString("secureMediaToken")
            if (mediaId.isBlank() || token.isBlank()) null else "${OnlineFallbackTransport.relayBaseUrl().trimEnd('/')}/relay/media/$mediaId?token=$token"
        }.getOrNull()
    }
}
