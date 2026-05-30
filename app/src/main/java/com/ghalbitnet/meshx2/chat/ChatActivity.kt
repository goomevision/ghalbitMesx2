package com.ghalbitnet.meshx2.chat

import android.Manifest
import android.content.ContentValues
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ghalbitnet.meshx2.MainActivity
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.call.CallSessionActivity
import com.ghalbitnet.meshx2.call.VoiceCallRegistry
import com.ghalbitnet.meshx2.identity.CentralIdentityResolver
import com.ghalbitnet.meshx2.identity.IdentityBridge
import com.ghalbitnet.meshx2.identity.IdentityDisplayFormatter
import com.ghalbitnet.meshx2.identity.IdentityRegistry
import com.ghalbitnet.meshx2.core.log.MeshLogger
import com.ghalbitnet.meshx2.core.utils.AppNotificationManager
import com.ghalbitnet.meshx2.core.utils.GhalbitDeepLinkRouter
import com.ghalbitnet.meshx2.file.FileTransferManager
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.contract.ActivityResultContracts
import com.ghalbitnet.meshx2.settings.ChatMediaSettingsManager
import com.ghalbitnet.meshx2.online.OnlineFallbackTransport
import com.ghalbitnet.meshx2.online.OnlinePresenceManager
import com.ghalbitnet.meshx2.online.PendingMessageStore
import com.ghalbitnet.meshx2.online.PreparedRouteManager
import com.ghalbitnet.meshx2.ui.ActionDebounceManager
import com.ghalbitnet.meshx2.ui.GhalbitTheme
import com.ghalbitnet.meshx2.ui.RuntimeLoadingOverlay
import com.ghalbitnet.meshx2.ui.RuntimeSoftBannerManager
import com.ghalbitnet.meshx2.ui.RuntimeUiState
import com.ghalbitnet.meshx2.ui.RuntimeUiSnapshot
import com.ghalbitnet.meshx2.ui.RuntimeUiStateManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ghalbitnet.meshx2.routing.PacketTtlManager
import com.ghalbitnet.meshx2.security.KeyStoreManager
import com.ghalbitnet.meshx2.core.utils.UiFeedbackManager
import com.ghalbitnet.meshx2.profile.ContactNameCardActivity
import com.ghalbitnet.meshx2.profile.ProfileRepository
import com.ghalbitnet.meshx2.profile.ProfileSyncManager
import com.ghalbitnet.meshx2.routing.CallRouteDiscoveryManager
import com.ghalbitnet.meshx2.routing.RouteSearchState
import com.ghalbitnet.meshx2.routing.RouteStateReconciler
import java.io.File
import com.ghalbitnet.meshx2.routing.TriplePathRoutePolicy
import com.ghalbitnet.meshx2.ui.CallSearchingToneManager
import com.ghalbitnet.meshx2.ui.RouteSearchingAnimator
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ChatActivity : AppCompatActivity() {
    companion object {
        private const val MAX_MESSAGE_LENGTH = 4096
        private const val MIN_VOICE_DURATION_MS = 700L

        @Volatile
        private var activePeerName: String? = null

        fun isViewingChatWith(peerName: String): Boolean {
            return activePeerName == peerName
        }
    }

    private lateinit var txtChat: TextView
    private lateinit var rvMessages: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var txtChatStatus: TextView
    private lateinit var txtRouteHealthStatus: TextView
    private lateinit var chatRoot: LinearLayout
    private lateinit var reviewPanel: LinearLayout
    private lateinit var reviewContentScroll: NestedScrollView
    private lateinit var txtReviewInlineStatus: TextView
    private lateinit var txtReviewTitle: TextView
    private lateinit var txtReviewMeta: TextView
    private lateinit var imgReviewPreview: ImageView
    private lateinit var edtReviewCaption: EditText
    private lateinit var btnReviewEdit: Button
    private lateinit var btnReviewReplace: Button
    private lateinit var btnReviewCancel: Button
    private lateinit var btnReviewConfirm: Button
    private lateinit var composerContainer: LinearLayout
    private lateinit var edtMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var btnCall: Button
    private lateinit var btnAttach: Button
    private lateinit var btnCamera: Button
    private lateinit var btnVoice: Button
    private lateinit var btnRetryFailed: Button
    private lateinit var runtimeLoadingOverlay: RuntimeLoadingOverlay
    private lateinit var runtimeSoftBanner: RuntimeSoftBannerManager

    private var peerIp: String = ""
    private var peerName: String = ""
    private var peerGlobalId: String? = null
    private var peerPublicKey: String? = null
    private var peerWalletAddress: String? = null
    private var peerDisplayName: String? = null
    private var activeConversationHint: ConversationOwnershipHint? = null
    private lateinit var chatDb: ChatDatabase
    private lateinit var draftDb: DraftDatabase
    private lateinit var keyStore: KeyStoreManager
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentRecordingFile: File? = null
    private var isRecording = false
    private var recordStartAt = 0L
    private var pendingVoiceStart = false
    private var recordingStatusJob: Job? = null
    private var routeHealthJob: Job? = null
    private var currentDraftId: String? = null
    private var currentReviewState: ReviewSendState = ReviewSendState.IDLE
    private var lastRuntimeSnapshot: RuntimeUiSnapshot = RuntimeUiStateManager.current()
    private var lastRouteStatusText: String = ""
    private var lastPreparedRouteLabel: String = ""
    private var routeStatusDefaultColor: Int = 0

    private val filePickerLauncher =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                createAttachmentDraft(
                    fileUri = uri,
                    contentType = resolveAttachmentContentType(uri),
                    displayName = readDisplayName(uri)
                )
            }
        }

    private val cameraPreviewLauncher =
        registerForActivityResult(
            ActivityResultContracts.TakePicturePreview()
        ) { bitmap ->
            if (bitmap != null) {
                createCapturedPhotoDraft(bitmap)
            } else {
                txtChatStatus.text = getString(R.string.chat_camera_cancelled)
            }
        }

    private val audioPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                if (pendingVoiceStart) {
                    startVoiceRecording()
                }
            } else {
                txtChatStatus.text = getString(R.string.chat_voice_permission)
                UiFeedbackManager.showToast(
                    this,
                    getString(R.string.chat_voice_permission)
                )
            }
            pendingVoiceStart = false
        }

    private val packetReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                val source =
                    intent?.getStringExtra("source") ?: "-"

                val rawPayload =
                    intent?.getStringExtra("payload") ?: ""

                val payload =
                    PacketTtlManager.extractMessage(rawPayload)

                val type =
                    intent?.getStringExtra("type") ?: ""

                if (source != peerName && type != "ACK") {
                    return
                }

                if (type == "FILE_CHUNK") {
                    return
                }

                if (type == "PING" || type == "PONG" || type == "ROUTE_CHECK") {
                    return
                }

                if (payload.isNotEmpty()) {
                    if (type == "ACK" || type == "CHAT_ACK" || type == "CHAT_DELIVERED") {
                        lifecycleScope.launch {
                            if (payload.isNotBlank()) {
                                withContext(Dispatchers.IO) {
                                    ChatDeliveryManager.handleAck(this@ChatActivity, payload)
                                }
                            }

                            runtimeSoftBanner.showMessage(
                                key = "chat:ack:$payload",
                                title = "Terkirim",
                                detail = "Pesan diterima oleh $source",
                                priority = 3,
                                durationMs = 1500L,
                                miniStatus = "Terhubung lokal"
                            )
                            renderHistory("Pesan diterima oleh $source")
                        }
                    } else if (type == "CHAT_READ") {
                        lifecycleScope.launch {
                            if (payload.isNotBlank()) {
                                withContext(Dispatchers.IO) {
                                    ChatDeliveryManager.handleRead(this@ChatActivity, payload)
                                }
                            }

                            runtimeSoftBanner.showMessage(
                                key = "chat:read:$payload",
                                title = "Dibaca",
                                detail = "Pesan dibuka oleh $source",
                                priority = 3,
                                durationMs = 1500L
                            )
                            renderHistory("Pesan dibaca oleh $source")
                        }
                    } else if (type == "AUDIO_RECEIVED") {
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                chatDb.chatDao().updateStatus(
                                    payload,
                                    "RECEIVED"
                                )
                            }

                            renderHistory("Pesan suara diterima oleh $source")
                        }
                    } else if (type == "AUDIO_PLAYED") {
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                chatDb.chatDao().updateStatus(
                                    payload,
                                    "PLAYED"
                                )
                            }

                            renderHistory("Pesan suara diputar oleh $source")
                        }
                    } else {
                        lifecycleScope.launch {
                            refreshConversationOwnershipHint(
                                ipHint = peerIp.ifBlank { null },
                                globalIdHint = activeConversationHint?.globalId,
                                publicKeyHint = activeConversationHint?.publicKey,
                                walletAddressHint = activeConversationHint?.walletAddress,
                                displayNameHint = source
                            )

                            withContext(Dispatchers.IO) {
                                val packetId =
                                    intent?.getStringExtra("packetId")
                                        ?.takeIf { it.isNotBlank() }
                                        ?: "IN-$source-${System.currentTimeMillis()}"

                                if (chatDb.chatDao().countByPacketId(packetId) == 0) {
                                    val internalEvent =
                                        InternalEventRouter.toChatMessage(
                                            context = this@ChatActivity,
                                            packetId = packetId,
                                            chatId = peerName,
                                            senderName = source,
                                            type = type,
                                            payload = payload,
                                            isSent = false,
                                            status = "DELIVERED",
                                            senderGlobalId = activeConversationHint?.globalId ?: peerGlobalId,
                                            publicDisplayName = peerDisplayName ?: source
                                        )
                                    val nextMessage =
                                        internalEvent
                                            ?: ChatMessage(
                                                packetId = packetId,
                                                chatId = peerName,
                                                senderName = source,
                                                content = if (type == "SOS") {
                                                    "SOS ALERT: $payload"
                                                } else {
                                                    payload
                                                },
                                                contentType = if (type == "SOS") "SOS" else "TEXT",
                                                isSent = false,
                                                status = "DELIVERED"
                                            )
                                    chatDb.chatDao().insertMessage(nextMessage)
                                }
                            }

                            renderHistory()
                        }
                    }
                }
            }
        }

    private val attachmentReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                val source =
                    intent?.getStringExtra(FileTransferManager.EXTRA_ATTACHMENT_SOURCE)
                        ?: return

            if (source != peerName) {
                return
            }

            lifecycleScope.launch {
                refreshConversationOwnershipHint(
                    ipHint = peerIp.ifBlank { null },
                    globalIdHint = activeConversationHint?.globalId,
                    publicKeyHint = activeConversationHint?.publicKey,
                    walletAddressHint = activeConversationHint?.walletAddress,
                    displayNameHint = source
                )

                val label =
                    intent.getStringExtra(FileTransferManager.EXTRA_ATTACHMENT_LABEL)

                    if (label.isNullOrBlank()) {
                        renderHistory()
                    } else {
                        renderHistory("$label diterima")
                    }
                }
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_chat)
        GhalbitTheme.applyWindow(this, "ChatActivity")

        peerIp =
            intent.getStringExtra("peerIp") ?: ""

        peerName =
            intent.getStringExtra("peerName")
                ?: intent.getStringExtra(GhalbitDeepLinkRouter.EXTRA_CONVERSATION_ID)
                ?: "UNKNOWN"
        peerGlobalId =
            intent.getStringExtra("peerGlobalId")
        peerPublicKey =
            intent.getStringExtra("peerPublicKey")
        peerWalletAddress =
            intent.getStringExtra("peerWalletAddress")
        peerDisplayName =
            intent.getStringExtra("peerDisplayName")

        val persistedConversationIdentity =
            ConversationIdentityStore.get(
                context = this,
                chatId = peerName
            )

        peerGlobalId =
            peerGlobalId ?: persistedConversationIdentity?.globalId
        peerPublicKey =
            peerPublicKey ?: persistedConversationIdentity?.publicKey
        peerWalletAddress =
            peerWalletAddress ?: persistedConversationIdentity?.walletAddress
        peerDisplayName =
            peerDisplayName ?: persistedConversationIdentity?.canonicalDisplayName

        // TODO unified identity:
        // chat session should be anchored by globalId, with peerName/IP kept
        // only as UI label and transport fallback during migration.
        chatDb =
            ChatDatabase.getInstance(this)
        draftDb =
            DraftDatabase.getInstance(this)

        keyStore =
            KeyStoreManager(this)

        if (peerIp.isBlank() && peerName.isNotBlank()) {
            peerIp = keyStore.getPeerAddress(peerName).orEmpty()
        }

        val bridgedIdentity =
            IdentityRegistry.findByLegacy(
                peerName = peerName,
                ipAddress = peerIp,
                publicKey = peerPublicKey
            )
                ?: IdentityRegistry.upsert(
                    IdentityBridge.fromChatPeer(
                        peerName = peerName,
                        peerIp = peerIp,
                        publicKey = peerPublicKey,
                        walletAddress = peerWalletAddress,
                        globalId = peerGlobalId,
                        displayName = peerDisplayName
                    )
                )

        peerGlobalId = bridgedIdentity.globalId
        peerPublicKey = bridgedIdentity.publicKey
        peerWalletAddress = bridgedIdentity.walletAddress
        peerDisplayName = bridgedIdentity.displayName

        refreshConversationOwnershipHint(
            ipHint = peerIp,
            globalIdHint = bridgedIdentity.globalId,
            publicKeyHint = bridgedIdentity.publicKey,
            walletAddressHint = bridgedIdentity.walletAddress,
            displayNameHint = bridgedIdentity.displayName
        )

        MeshLogger.i(
            "CHAT_IDENTITY",
            IdentityDisplayFormatter.secondaryLabel(
                primaryLabel = IdentityDisplayFormatter.primaryLabel(
                    canonicalDisplayName = peerDisplayName,
                    walletAddress = peerWalletAddress,
                    globalId = peerGlobalId,
                    publicKey = peerPublicKey,
                    legacyName = peerName,
                    ipAddress = peerIp
                ),
                legacyName = peerName,
                walletAddress = peerWalletAddress,
                globalId = peerGlobalId,
                publicKey = peerPublicKey,
                ipAddress = peerIp
            ) ?: "Unknown peer"
        )

        txtChat =
            findViewById(R.id.txtChatHeader)
        txtChat.setOnClickListener {
            startActivity(
                ContactNameCardActivity.createIntent(
                    context = this,
                    globalId = activeConversationHint?.globalId ?: peerGlobalId,
                    chatId = peerName,
                    fallbackName = peerDisplayName ?: peerName,
                    publicKeyHash = activeConversationHint?.publicKey?.let { com.ghalbitnet.meshx2.call.CallManager.publicKeyHash(it) },
                    routeHint = activeConversationHint?.lastKnownIp ?: peerIp
                )
            )
            Log.d("GHALBIT-CARD", "opened from chat")
        }

        txtChatStatus =
            findViewById(R.id.txtChatStatus)
        txtRouteHealthStatus =
            findViewById(R.id.txtRouteHealthStatus)
        chatRoot =
            findViewById(R.id.chatRoot)
        reviewPanel =
            findViewById(R.id.reviewPanel)
        reviewContentScroll =
            findViewById(R.id.reviewContentScroll)
        txtReviewInlineStatus =
            findViewById(R.id.txtReviewInlineStatus)
        txtReviewTitle =
            findViewById(R.id.txtReviewTitle)
        txtReviewMeta =
            findViewById(R.id.txtReviewMeta)
        imgReviewPreview =
            findViewById(R.id.imgReviewPreview)
        edtReviewCaption =
            findViewById(R.id.edtReviewCaption)
        btnReviewEdit =
            findViewById(R.id.btnReviewEdit)
        btnReviewReplace =
            findViewById(R.id.btnReviewReplace)
        btnReviewCancel =
            findViewById(R.id.btnReviewCancel)
        btnReviewConfirm =
            findViewById(R.id.btnReviewConfirm)
        GhalbitTheme.logCardRendered("chat-header")

        rvMessages =
            findViewById(R.id.rvMessages)

        chatAdapter =
            ChatAdapter(
                onMessageClick = { message ->
                    handleMessageClick(message)
                },
                onMessageLongClick = { message ->
                    showMessageActions(message)
                }
            )

        rvMessages.layoutManager =
            LinearLayoutManager(this).apply {
                stackFromEnd = true
            }

        rvMessages.adapter =
            chatAdapter

        edtMessage =
            findViewById(R.id.edtMessage)
        composerContainer =
            findViewById(R.id.composerContainer)

        btnSend =
            findViewById(R.id.btnSend)

        btnCall =
            findViewById(R.id.btnCall)

        btnAttach =
            findViewById(R.id.btnAttach)

        btnCamera =
            findViewById(R.id.btnCamera)

        btnVoice =
            findViewById(R.id.btnVoice)

        btnRetryFailed =
            findViewById(R.id.btnRetryFailed)
        routeStatusDefaultColor = txtRouteHealthStatus.currentTextColor
        lastRouteStatusText = txtRouteHealthStatus.text?.toString().orEmpty()

        val headerName =
            IdentityDisplayFormatter.primaryLabel(
                canonicalDisplayName = peerDisplayName,
                walletAddress = peerWalletAddress,
                globalId = peerGlobalId,
                publicKey = peerPublicKey,
                legacyName = peerName,
                ipAddress = peerIp
            )
        val headerHint =
            IdentityDisplayFormatter.secondaryLabel(
                primaryLabel = headerName,
                legacyName = peerName,
                walletAddress = peerWalletAddress,
                globalId = peerGlobalId,
                publicKey = peerPublicKey,
                ipAddress = peerIp
            )
        txtChat.text =
            buildString {
                append("Chat with ")
                append(headerName)
                headerHint?.let {
                    append("\n")
                    append(it)
                }
            }

        lifecycleScope.launch {
            renderHistory()
            restoreDraftIfNeeded()
        }
        lifecycleScope.launch(Dispatchers.IO) {
            peerGlobalId?.takeIf { it.isNotBlank() }?.let {
                ProfileSyncManager.fetchProfile(this@ChatActivity, it)
            }
        }
        lifecycleScope.launch {
            peerGlobalId?.takeIf { it.isNotBlank() }?.let { globalId ->
                val candidate = PreparedRouteManager.requestSecondaryRoute(this@ChatActivity, "chat-$peerName", globalId, "LOCAL_MESH_DIRECT")
                val validated = PreparedRouteManager.validateSecondaryRoute(this@ChatActivity, candidate)
                lastPreparedRouteLabel = PreparedRouteManager.statusLabel(validated)
                updateConversationRouteStatus()
            } ?: run {
                lastPreparedRouteLabel = PreparedRouteManager.statusLabel(null)
                if (!OnlineFallbackTransport.isConfigured()) {
                    Log.d("GHALBIT-ROUTE-UI", "relay missing shown")
                }
            }
        }

        if (intent.getStringExtra(GhalbitDeepLinkRouter.EXTRA_OPEN_MODE) == GhalbitDeepLinkRouter.MODE_CHAT_MESSAGE) {
            GhalbitDeepLinkRouter.logChatOpen(peerName)
            Log.d("GHALBIT-NOTIFY", "message clicked id=${intent.getStringExtra(GhalbitDeepLinkRouter.EXTRA_MESSAGE_ID).orEmpty()}")
        }

        txtChatStatus.text = getString(R.string.chat_voice_hold_hint)
        updateConversationRouteStatus()
        RuntimeUiStateManager.bind(applicationContext)
        runtimeLoadingOverlay = RuntimeLoadingOverlay.attach(this)
        runtimeSoftBanner = RuntimeSoftBannerManager.attach(this)
        setupReviewInsets()
        setReviewMode(false)
        observeRuntimeUiState()

        btnSend.setOnClickListener {
            if (!ActionDebounceManager.allow("chat:send:$peerName", RuntimeUiStateManager.current().actionsLocked, cooldownMs = 600L)) {
                return@setOnClickListener
            }
            openTextReview()
        }

        btnCall.setOnClickListener {
            if (!ActionDebounceManager.allow("chat:call:$peerName", RuntimeUiStateManager.current().actionsLocked, cooldownMs = 1400L)) {
                return@setOnClickListener
            }
            startCallSession()
        }

        btnAttach.setOnClickListener {
            if (!ActionDebounceManager.allow("chat:attach:$peerName", RuntimeUiStateManager.current().actionsLocked, cooldownMs = 700L)) {
                return@setOnClickListener
            }
            filePickerLauncher.launch("*/*")
        }

        btnCamera.setOnClickListener {
            if (!ActionDebounceManager.allow("chat:camera:$peerName", RuntimeUiStateManager.current().actionsLocked, cooldownMs = 700L)) {
                return@setOnClickListener
            }
            cameraPreviewLauncher.launch(null)
        }

        btnVoice.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    beginPushToTalk()
                    true
                }

                MotionEvent.ACTION_UP -> {
                    finishPushToTalk(sendMessage = true)
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    finishPushToTalk(sendMessage = false)
                    true
                }

                else -> false
            }
        }

        btnRetryFailed.setOnClickListener {
            if (!ActionDebounceManager.allow("chat:retry:$peerName", RuntimeUiStateManager.current().actionsLocked, cooldownMs = 900L)) {
                return@setOnClickListener
            }
            retryLastFailedMessage()
        }

        btnReviewEdit.setOnClickListener {
            editCurrentDraft()
        }
        btnReviewReplace.setOnClickListener {
            replaceCurrentDraftAttachment()
        }
        btnReviewCancel.setOnClickListener {
            cancelCurrentDraft()
        }
        btnReviewConfirm.setOnClickListener {
            confirmCurrentDraft()
        }
    }

    override fun onResume() {
        super.onResume()
        runtimeLoadingOverlay.onHostResume()
        runtimeSoftBanner.onHostResume()
        activePeerName = peerName
        OnlinePresenceManager.bind(this)
        RuntimeUiStateManager.setTransientState(
            source = "chat:$peerName",
            state = RuntimeUiState.CONNECTING,
            title = "Mencari jalur terbaik...",
            detail = "Sistem sedang memilih jalur komunikasi."
        )
        AppNotificationManager.clearChatNotifications(this, peerName)
        Log.d("GHALBIT-READ", "local conversation opened chatId=$peerName")
        ChatDeliveryManager.markChatReadRemotely(this, peerName, peerGlobalId)
        ConversationKeepAliveManager.startConversation(
            context = this,
            chatId = peerName,
            globalId = peerGlobalId,
            routeHint = peerIp
        )
        routeHealthJob?.cancel()
        routeHealthJob =
            lifecycleScope.launch {
                while (true) {
                    updateConversationRouteStatus()
                    val routeSnapshot = ConversationKeepAliveManager.snapshot(peerName)
                    if (routeSnapshot != null) {
                        RuntimeUiStateManager.onRouteHealth(
                            routeSnapshot.routeHealth,
                            "Jalur aktif: ${routeSnapshot.transport} | loss=${routeSnapshot.packetLossEstimate}%"
                        )
                    } else if (RouteStateReconciler.shouldSuppressPending(peerName, peerGlobalId)) {
                        val active = RouteStateReconciler.current(peerName, peerGlobalId)
                        if (active != null) {
                            RuntimeUiStateManager.setTransientState(
                                source = "chat:$peerName",
                                state = RouteStateReconciler.runtimeState(active),
                                title =
                                    when (active.state) {
                                        RouteSearchState.ROUTE_PROBING -> "Menguji jalur"
                                        RouteSearchState.ROUTE_SWITCHING -> "Mencoba jalur lain"
                                        else -> "Mencari jalur"
                                    },
                                detail = active.label,
                                actionsLocked = false
                            )
                        }
                    } else if (OnlinePresenceManager.getOnlineRoute(this@ChatActivity, peerGlobalId.orEmpty()) != null) {
                        RuntimeUiStateManager.setTransientState(
                            source = "chat:$peerName",
                            state = RuntimeUiState.INTERNET_FALLBACK,
                            title = "Menggunakan jalur internet",
                            detail = "Komunikasi dipertahankan lewat relay internet.",
                            actionsLocked = false
                        )
                    } else if (PendingMessageStore.countForChat(this@ChatActivity, peerName) > 0) {
                        RuntimeUiStateManager.setTransientState(
                            source = "chat:$peerName",
                            state = RuntimeUiState.OFFLINE_PENDING,
                            title = "Menunggu koneksi tersedia",
                            detail = "Pesan disimpan sementara sampai jalur kembali siap.",
                            actionsLocked = false
                        )
                    }
                    delay(1500L)
                }
            }

        LocalBroadcastManager
            .getInstance(this)
            .registerReceiver(
                packetReceiver,
                IntentFilter(
                    "com.ghalbitnet.meshx2.NEW_MESH_PACKET"
                )
            )

        LocalBroadcastManager
            .getInstance(this)
            .registerReceiver(
                attachmentReceiver,
                IntentFilter(
                    FileTransferManager.ACTION_ATTACHMENT_MESSAGE_RECEIVED
                )
            )
    }

    override fun onPause() {
        runtimeLoadingOverlay.onHostPause()
        runtimeSoftBanner.onHostPause()
        super.onPause()
        if (activePeerName == peerName) {
            activePeerName = null
        }
        RuntimeUiStateManager.clearTransientState("chat:$peerName")

        ConversationKeepAliveManager.stopConversation(peerName)
        routeHealthJob?.cancel()
        routeHealthJob = null

        stopVoiceRecording(cancelOnly = true)
        recordingStatusJob?.cancel()
        recordingStatusJob = null
        releasePlayer()

        LocalBroadcastManager
            .getInstance(this)
            .unregisterReceiver(
                packetReceiver
            )

        LocalBroadcastManager
            .getInstance(this)
            .unregisterReceiver(
                attachmentReceiver
            )
    }

    override fun onDestroy() {
        runtimeLoadingOverlay.onHostDestroy()
        runtimeSoftBanner.onHostDestroy()
        super.onDestroy()
    }

    private fun beginPushToTalk() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingVoiceStart = true
            audioPermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
            return
        }

        startVoiceRecording()
    }

    private fun finishPushToTalk(sendMessage: Boolean) {
        pendingVoiceStart = false
        if (!isRecording) {
            return
        }
        stopVoiceRecording(cancelOnly = !sendMessage)
    }

    private fun startVoiceRecording() {
        if (isRecording) {
            return
        }

        try {
            val voiceDir = File(cacheDir, "voice_notes")
            if (!voiceDir.exists()) {
                voiceDir.mkdirs()
            }

            val outputFile =
                File(
                    voiceDir,
                    "voice_${System.currentTimeMillis()}.m4a"
                )

            val recorder =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(this)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(22050)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            currentRecordingFile = outputFile
            isRecording = true
            recordStartAt = System.currentTimeMillis()
            btnVoice.text = getString(R.string.chat_voice_stop)
            startRecordingStatusTicker()
        } catch (e: Exception) {
            MeshLogger.e("CHAT", "Voice record start failed", e)
            txtChatStatus.text = getString(R.string.chat_voice_failed)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.chat_voice_failed)
            )
            stopVoiceRecording(cancelOnly = true)
        }
    }

    private fun stopVoiceRecording(cancelOnly: Boolean) {
        val recorder = mediaRecorder
        val recordedFile = currentRecordingFile

        mediaRecorder = null
        currentRecordingFile = null
        val wasRecording = isRecording
        isRecording = false
        val durationMs =
            System.currentTimeMillis() - recordStartAt
        recordStartAt = 0L
        recordingStatusJob?.cancel()
        recordingStatusJob = null
        btnVoice.text = getString(R.string.chat_voice_start)

        try {
            recorder?.stop()
        } catch (_: Exception) {
        }

        try {
            recorder?.reset()
        } catch (_: Exception) {
        }

        try {
            recorder?.release()
        } catch (_: Exception) {
        }

        if (cancelOnly || !wasRecording || recordedFile == null || !recordedFile.exists()) {
            recordedFile?.delete()
            if (!cancelOnly) {
                txtChatStatus.text = getString(R.string.chat_voice_failed)
            }
            return
        }

        if (durationMs < MIN_VOICE_DURATION_MS) {
            recordedFile.delete()
            txtChatStatus.text = getString(R.string.chat_voice_too_short)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.chat_voice_too_short)
            )
            return
        }

        txtChatStatus.text = getString(R.string.chat_voice_ready)
        sendVoiceMessage(recordedFile, durationMs)
    }

    private fun sendVoiceMessage(
        audioFile: File,
        durationMs: Long
    ) {
        val packetId =
            "AUDIO-" + System.currentTimeMillis()
        val voiceLabel =
            buildVoiceLabel(durationMs)
        val ownershipHint =
            refreshConversationOwnershipHint()

        lifecycleScope.launch {
            btnSend.isEnabled = false
            btnAttach.isEnabled = false
            btnCamera.isEnabled = false
            btnVoice.isEnabled = false
            btnRetryFailed.isEnabled = false
            txtChatStatus.text = getString(R.string.chat_voice_sending)

            withContext(Dispatchers.IO) {
                chatDb.chatDao().insertMessage(
                    ChatMessage(
                        packetId = packetId,
                        chatId = peerName,
                        senderName = "ME",
                        content = voiceLabel,
                        contentType = "AUDIO",
                        filePath = audioFile.absolutePath,
                        isSent = true,
                        status = "SENDING"
                    )
                )
            }

            renderHistory()

            FileTransferManager.sendFile(
                context = this@ChatActivity,
                fileUri = android.net.Uri.fromFile(audioFile),
                destinationPeerId = peerName,
                keyStore = keyStore,
                myPeerId = MainActivity.myGlobalPeerId,
                listener =
                    object : FileTransferManager.TransferStatusListener {
                        override fun onProgress(
                            message: String,
                            busy: Boolean
                        ) {
                            runOnUiThread {
                                txtChatStatus.text =
                                    getString(R.string.chat_voice_sending)
                            }
                        }

                        override fun onComplete(message: String) {
                            lifecycleScope.launch {
                                refreshConversationOwnershipHint(
                                    ipHint = peerIp.ifBlank { null },
                                    globalIdHint = ownershipHint.globalId,
                                    publicKeyHint = ownershipHint.publicKey,
                                    walletAddressHint = ownershipHint.walletAddress,
                                    displayNameHint = ownershipHint.canonicalDisplayName
                                )

                                withContext(Dispatchers.IO) {
                                    chatDb.chatDao().updateStatus(
                                        packetId,
                                        "SENT"
                                    )
                                }

                                txtChatStatus.text =
                                    getString(R.string.chat_voice_sent)
                                renderHistory()
                                restoreChatButtons()
                            }
                        }

                        override fun onError(message: String) {
                            lifecycleScope.launch {
                                refreshConversationOwnershipHint(
                                    ipHint = peerIp.ifBlank { null },
                                    globalIdHint = ownershipHint.globalId,
                                    publicKeyHint = ownershipHint.publicKey,
                                    walletAddressHint = ownershipHint.walletAddress,
                                    displayNameHint = ownershipHint.canonicalDisplayName
                                )

                                withContext(Dispatchers.IO) {
                                    chatDb.chatDao().updateStatus(
                                        packetId,
                                        "FAILED"
                                    )
                                }

                                txtChatStatus.text =
                                    getString(R.string.chat_voice_failed)
                                UiFeedbackManager.showToast(
                                    this@ChatActivity,
                                    getString(R.string.chat_voice_failed)
                                )
                                renderHistory()
                                restoreChatButtons()
                            }
                        }
                    }
            )
        }
    }

    private fun playAudioMessage(message: ChatMessage) {
        val path =
            message.filePath

        if (path.isNullOrBlank() || !File(path).exists()) {
            txtChatStatus.text = getString(R.string.chat_voice_missing)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.chat_voice_missing)
            )
            return
        }

        try {
            releasePlayer()
            chatAdapter.setPlayingMessage(message.packetId)

            mediaPlayer =
                MediaPlayer().apply {
                    setDataSource(path)
                    setOnCompletionListener {
                        if (!message.isSent) {
                            FileTransferManager.sendAudioStatusSignal(
                                context = this@ChatActivity,
                                targetPeerId = peerName,
                                packetType = "AUDIO_PLAYED",
                                referencePacketId = message.packetId
                            )
                        }
                        releasePlayer()
                        chatAdapter.setPlayingMessage(null)
                        txtChatStatus.text = getString(R.string.chat_idle)
                    }
                    prepare()
                    start()
                }

            txtChatStatus.text =
                "${getString(R.string.chat_voice_playing)} ${message.content}"
        } catch (e: Exception) {
            MeshLogger.e("CHAT", "Audio play failed", e)
            chatAdapter.setPlayingMessage(null)
            txtChatStatus.text = getString(R.string.chat_voice_failed)
        }
    }

    private fun handleMessageClick(message: ChatMessage) {
        when (message.contentType) {
            "AUDIO" -> playAudioMessage(message)
            "IMAGE" -> openImagePreview(message)
            "FILE" -> openSharedFile(message)
        }
    }

    private fun openImagePreview(message: ChatMessage) {
        val path = message.filePath
        if (path.isNullOrBlank() || !File(path).exists()) {
            txtChatStatus.text = getString(R.string.chat_file_missing)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.chat_file_missing)
            )
            return
        }

        startActivity(
            ImageViewerActivity.createIntent(
                context = this,
                filePath = path,
                title = message.content
            )
        )
    }

    private fun openSharedFile(message: ChatMessage) {
        val path = message.filePath
        if (path.isNullOrBlank() || !File(path).exists()) {
            txtChatStatus.text = getString(R.string.chat_file_missing)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.chat_file_missing)
            )
            return
        }

        try {
            val file = File(path)
            val uri =
                FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )

            val mimeType =
                MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(file.extension.lowercase())
                    ?: "application/octet-stream"

            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

            startActivity(Intent.createChooser(intent, getString(R.string.chat_open_file)))
        } catch (e: Exception) {
            MeshLogger.e("CHAT", "Open file failed", e)
            txtChatStatus.text = getString(R.string.chat_open_file_failed)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.chat_open_file_failed)
            )
        }
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }

        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }

        mediaPlayer = null
        chatAdapter.setPlayingMessage(null)
    }

    private fun startCallSession() {
        if (VoiceCallRegistry.isBusy()) {
            txtChatStatus.text = getString(R.string.call_busy_local)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.call_busy_local)
            )
            return
        }

        val endpoint =
            com.ghalbitnet.meshx2.call.CallManager.resolvePeer(
                context = this,
                peerName = peerName,
                ipHint = peerIp,
                globalIdHint = activeConversationHint?.globalId ?: peerGlobalId,
                publicKeyHint = activeConversationHint?.publicKey ?: peerPublicKey,
                walletAddressHint = activeConversationHint?.walletAddress ?: peerWalletAddress,
                displayNameHint = activeConversationHint?.canonicalDisplayName ?: peerDisplayName
            )
        val localGlobalId =
            com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager.buildGlobalId(keyStore.publicKeyBase64)
        val localPublicKeyHash =
            com.ghalbitnet.meshx2.call.CallManager.localPublicKeyHash(this)
        if (
            com.ghalbitnet.meshx2.call.CallManager.isSelfCall(
                localNodeId = MainActivity.myGlobalPeerId,
                localGlobalId = localGlobalId,
                localPublicKeyHash = localPublicKeyHash,
                peer = endpoint
            )
        ) {
            txtChatStatus.text = getString(R.string.call_self_ignored)
            UiFeedbackManager.showToast(this, getString(R.string.call_self_ignored))
            return
        }
        val callId = UUID.randomUUID().toString()
        val toneManager = CallSearchingToneManager()
        val animator = RouteSearchingAnimator(lifecycleScope) { text -> txtChatStatus.text = text }
        btnCall.isEnabled = false
        animator.start("Mencari jalur ke kontak")
        toneManager.start()
        runtimeSoftBanner.showMessage(
            key = "call:search:$callId",
            title = "Mencari jalur ke kontak",
            detail = "Sistem sedang memilih jalur terbaik untuk panggilan.",
            priority = 3,
            durationMs = 2200L,
            miniStatus = "Mencari..."
        )
        lifecycleScope.launch {
            try {
                val discovery =
                    CallRouteDiscoveryManager.discoverForCall(
                        context = this@ChatActivity,
                        peerName = peerName,
                        ipHint = peerIp,
                        globalIdHint = endpoint.globalId,
                        publicKeyHint = endpoint.publicKey,
                        walletAddressHint = endpoint.walletAddress,
                        displayNameHint = endpoint.displayName
                    ) { _, label ->
                        animator.update(label)
                    }
                val resolvedEndpoint = discovery.endpoint
                if (resolvedEndpoint == null) {
                    animator.stop(discovery.humanStatus)
                    runtimeSoftBanner.showMessage(
                        key = "call:search:failed:$callId",
                        title = "Belum menemukan jalur",
                        detail = "Belum menemukan jalur. Pencarian tetap berjalan.",
                        priority = 4,
                        durationMs = 2600L,
                        miniStatus = "Mencari..."
                    )
                    UiFeedbackManager.showToast(this@ChatActivity, "Belum menemukan jalur. Pencarian tetap berjalan.")
                    return@launch
                }
                val targetIp = resolvedEndpoint.routeHint ?: resolvedEndpoint.transportIp
                if (targetIp.isNullOrBlank()) {
                    animator.stop(getString(R.string.call_peer_missing))
                    UiFeedbackManager.showToast(this@ChatActivity, getString(R.string.call_peer_missing))
                    return@launch
                }
                animator.stop("Jalur ditemukan")
                runtimeSoftBanner.showMessage(
                    key = "call:search:ok:$callId",
                    title = "Jalur ditemukan",
                    detail = routeTypeLabel(discovery.selectedRouteType),
                    priority = 2,
                    durationMs = 1600L,
                    miniStatus = routeTypeLabel(discovery.selectedRouteType)
                )
                startActivity(
                    CallSessionActivity.createIntent(
                        context = this@ChatActivity,
                        peerName = peerName,
                        peerIp = targetIp,
                        callId = callId,
                        incoming = false,
                        peerGlobalId = resolvedEndpoint.globalId,
                        peerPublicKey = resolvedEndpoint.publicKey,
                        peerWalletAddress = resolvedEndpoint.walletAddress,
                        peerDisplayName = resolvedEndpoint.displayName
                    )
                )
            } finally {
                toneManager.stopAndRelease()
                btnCall.isEnabled = true
            }
        }
    }

    private fun routeTypeLabel(routeType: String?): String {
        return when (routeType) {
            TriplePathRoutePolicy.SERVER_DIRECT_INTERNET -> "Server induk siap"
            TriplePathRoutePolicy.INTERNET_RELAY -> "Relay internet aktif"
            TriplePathRoutePolicy.LOCAL_MESH_PRIMARY -> "Node lokal terdekat"
            TriplePathRoutePolicy.LOCAL_MESH_SECONDARY -> "Jalur mesh cadangan"
            TriplePathRoutePolicy.IDENTITY_COPY_TRACE -> "Jejak copy identitas"
            TriplePathRoutePolicy.STORE_FORWARD -> "Fallback simpan-kirim"
            else -> "Menghubungkan panggilan"
        }
    }

    private fun createCapturedPhotoDraft(bitmap: Bitmap) {
        val photoDir =
            if (ChatMediaSettingsManager.shouldKeepCapturedPhotos(this)) {
                File(filesDir, "sent_media/camera_shots")
            } else {
                File(cacheDir, "camera_shots")
            }
        if (!photoDir.exists()) {
            photoDir.mkdirs()
        }

        val imageFile =
            File(photoDir, "photo_${System.currentTimeMillis()}.jpg")

        try {
            imageFile.outputStream().use { output ->
                bitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    ChatMediaSettingsManager.getPhotoQualityPercent(this),
                    output
                )
            }

            createAttachmentDraft(
                fileUri = Uri.fromFile(imageFile),
                contentType = "IMAGE",
                displayName = imageFile.name
            )
        } catch (e: Exception) {
            MeshLogger.e("CHAT", "Camera photo save failed", e)
            txtChatStatus.text = getString(R.string.chat_camera_failed)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.chat_camera_failed)
            )
        }
    }

    private fun createAttachmentDraft(
        fileUri: Uri,
        contentType: String,
        displayName: String
    ) {
        val localAttachment =
            DraftAttachmentStore.copyToDraft(this, fileUri, displayName)

        if (localAttachment == null) {
            txtChatStatus.text = getString(R.string.chat_file_prepare_failed)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.chat_file_prepare_failed)
            )
            return
        }

        lifecycleScope.launch {
            val metadata =
                FileMetadataReader.fromFile(
                    context = this@ChatActivity,
                    file = localAttachment,
                    fallbackMimeType = contentResolver.getType(fileUri).orEmpty(),
                    contentType = contentType
                )
            val draftId = "DRAFT-${System.currentTimeMillis()}"
            withContext(Dispatchers.IO) {
                draftDb.draftMessageDao().findLatestDraft(peerName)
                    ?.let { existing -> draftDb.draftMessageDao().findAttachment(existing.draftId) }
                    ?.let { DraftAttachmentStore.remove(it.filePath) }
                draftDb.draftMessageDao().replaceDraft(
                    DraftMessageEntity(
                        draftId = draftId,
                        chatId = peerName,
                        draftType = if (contentType == "IMAGE") ChatDeliveryState.DRAFT_MEDIA.dbValue else ChatDeliveryState.DRAFT_FILE.dbValue,
                        content = edtMessage.text.toString().trim(),
                        status = ChatDeliveryState.REVIEW_READY.dbValue,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    ),
                    DraftAttachmentEntity(
                        draftId = draftId,
                        chatId = peerName,
                        contentType = contentType,
                        filePath = localAttachment.absolutePath,
                        displayName = metadata.displayName,
                        mimeType = metadata.mimeType,
                        fileSize = metadata.fileSize,
                        warning = metadata.warning
                    )
                )
            }
            currentDraftId = draftId
            currentReviewState = ReviewSendState.REVIEW_READY
            Log.d("GHALBIT-DRAFT", "created type=${if (contentType == "IMAGE") "IMAGE" else "FILE"}")
            Log.d("GHALBIT-SAFE-SEND", "draft created")
            Log.d("GHALBIT-REVIEW", "open type=$contentType")
            renderDraftReview()
        }
    }

    private fun confirmAttachmentDraftSend(draft: DraftMessage, attachment: DraftAttachment) {
        val packetId = "FILE-" + System.currentTimeMillis()
        val messageId = "MSG-" + System.currentTimeMillis()
        val label =
            when (attachment.contentType) {
                "IMAGE" -> if (draft.content.isBlank()) getString(R.string.chat_image_label_simple) else draft.content
                else -> if (draft.content.isBlank()) getString(R.string.chat_file_label, attachment.displayName) else draft.content
            }
        val ownershipHint = refreshConversationOwnershipHint()

        lifecycleScope.launch {
            disableChatButtons()
            txtChatStatus.text = if (attachment.contentType == "IMAGE") getString(R.string.chat_image_sending) else getString(R.string.chat_file_sending)

            withContext(Dispatchers.IO) {
                chatDb.chatDao().insertMessage(
                    ChatMessage(
                        packetId = packetId,
                        chatId = peerName,
                        senderName = "ME",
                        content = label,
                        contentType = attachment.contentType,
                        filePath = attachment.filePath,
                        isSent = true,
                        status = ChatDeliveryState.QUEUED_LOCAL.dbValue
                    )
                )
            }

            renderHistory()

            val routeHealth = ConversationKeepAliveManager.snapshot(peerName)?.routeHealth
            val waitingForPeer =
                peerIp.isBlank() ||
                    routeHealth == RouteHealthStatus.OFFLINE_PENDING ||
                    routeHealth == RouteHealthStatus.RECONNECTING

            withContext(Dispatchers.IO) {
                ChatDeliveryManager.queueMediaPending(
                    context = this@ChatActivity,
                    packetId = packetId,
                    messageId = messageId,
                    chatId = peerName,
                    label = label,
                    filePath = attachment.filePath,
                    mediaType = attachment.contentType,
                    mimeType = attachment.mimeType,
                    fileSize = attachment.fileSize,
                    routeHint = peerIp.ifBlank { null },
                    peerGlobalId = ownershipHint.globalId,
                    peerPublicKey = ownershipHint.publicKey,
                    peerWalletAddress = ownershipHint.walletAddress,
                    peerDisplayName = ownershipHint.canonicalDisplayName,
                    waitingForPeer = waitingForPeer,
                    lastErrorReason = if (waitingForPeer) "peerOffline" else null
                )
            }

            if (!waitingForPeer) {
                FileTransferManager.sendFile(
                    context = this@ChatActivity,
                    fileUri = Uri.fromFile(File(attachment.filePath)),
                    destinationPeerId = peerName,
                    keyStore = keyStore,
                    myPeerId = MainActivity.myGlobalPeerId,
                    listener =
                        object : FileTransferManager.TransferStatusListener {
                            override fun onProgress(message: String, busy: Boolean) {
                                runOnUiThread { txtChatStatus.text = message }
                            }

                            override fun onComplete(message: String) {
                                lifecycleScope.launch {
                                    withContext(Dispatchers.IO) {
                                        chatDb.chatDao().updateStatus(packetId, ChatDeliveryState.SENT_LOCAL.dbValue)
                                        PendingMessageStore.remove(this@ChatActivity, packetId)
                                    }
                                    txtChatStatus.text = message
                                    runtimeSoftBanner.showMessage(
                                        key = "media:sent:$packetId",
                                        title = "Terkirim",
                                        detail = message,
                                        priority = 3,
                                        durationMs = 1500L
                                    )
                                    renderHistory()
                                    restoreChatButtons()
                                }
                            }

                            override fun onError(message: String) {
                                lifecycleScope.launch {
                                    withContext(Dispatchers.IO) {
                                        ChatDeliveryManager.queueMediaPending(
                                            context = this@ChatActivity,
                                            packetId = packetId,
                                            messageId = messageId,
                                            chatId = peerName,
                                            label = label,
                                            filePath = attachment.filePath,
                                            mediaType = attachment.contentType,
                                            mimeType = attachment.mimeType,
                                            fileSize = attachment.fileSize,
                                            routeHint = peerIp.ifBlank { null },
                                            peerGlobalId = ownershipHint.globalId,
                                            peerPublicKey = ownershipHint.publicKey,
                                            peerWalletAddress = ownershipHint.walletAddress,
                                            peerDisplayName = ownershipHint.canonicalDisplayName,
                                            waitingForPeer = message.contains("alamat", ignoreCase = true) || message.contains("belum tersedia", ignoreCase = true),
                                            lastErrorReason = message
                                        )
                                    }
                                    txtChatStatus.text = "Menunggu koneksi"
                                    runtimeSoftBanner.showMessage(
                                        key = "media:retry:$packetId",
                                        title = "Akan dikirim otomatis",
                                        detail = "Media disimpan dulu sampai penerima atau jalur tersedia.",
                                        priority = 4,
                                        durationMs = 2400L,
                                        miniStatus = "Menunggu koneksi"
                                    )
                                    renderHistory()
                                    restoreChatButtons()
                                }
                            }
                        }
                )
            } else {
                txtChatStatus.text = "Menunggu penerima online"
                txtRouteHealthStatus.text = "Menunggu koneksi"
                runtimeSoftBanner.showMessage(
                    key = "media:pending:$packetId",
                    title = "Menunggu penerima online",
                    detail = "Media akan dikirim otomatis saat jalur tersedia.",
                    priority = 4,
                    durationMs = 2200L,
                    miniStatus = "Menunggu koneksi"
                )
                restoreChatButtons()
            }

            cancelCurrentDraft(cleanOnly = true, keepAttachment = true)
            edtMessage.setText("")
        }
    }

    private fun cacheLocalAttachment(
        fileUri: Uri,
        displayName: String
    ): File? {
        return try {
            val attachDir = File(cacheDir, "chat_attachments")
            if (!attachDir.exists()) {
                attachDir.mkdirs()
            }

            val safeName =
                displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val localFile =
                createUniqueAttachmentFile(attachDir, safeName)

            contentResolver.openInputStream(fileUri)?.use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            localFile
        } catch (e: Exception) {
            MeshLogger.e("CHAT", "Cache attachment failed", e)
            null
        }
    }

    private fun createUniqueAttachmentFile(
        directory: File,
        safeName: String
    ): File {
        val dotIndex = safeName.lastIndexOf('.')
        val baseName =
            if (dotIndex > 0) safeName.substring(0, dotIndex) else safeName
        val extension =
            if (dotIndex > 0) safeName.substring(dotIndex) else ""

        var candidate = File(directory, safeName)
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(directory, "${baseName}_$suffix$extension")
            suffix++
        }

        return candidate
    }

    private fun restoreChatButtons() {
        btnSend.isEnabled = true
        btnAttach.isEnabled = true
        btnCamera.isEnabled = true
        btnVoice.isEnabled = true
        btnRetryFailed.isEnabled = true
        btnSend.text = getString(R.string.send)
        btnVoice.text = getString(R.string.chat_voice_start)
    }

    private fun setupReviewInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(chatRoot) { _, insets ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val baseBottom = maxOf(navInsets.bottom, dp(8))
            composerContainer.updatePadding(bottom = baseBottom)
            reviewPanel.updatePadding(bottom = baseBottom)
            if (reviewPanel.visibility == View.VISIBLE) {
                applyReviewResponsiveLayout(insets)
                Log.d("GHALBIT-REVIEW-LAYOUT", "nav inset applied")
            }
            insets
        }
    }

    private fun setReviewMode(enabled: Boolean) {
        composerContainer.visibility = if (enabled) View.GONE else View.VISIBLE
        runtimeSoftBanner.visibility = if (enabled) View.GONE else View.VISIBLE
        if (enabled) {
            Log.d("GHALBIT-REVIEW-LAYOUT", "main composer hidden")
            applyCompactRouteStatus()
            updateReviewInlineStatus(lastRuntimeSnapshot)
        } else {
            composerContainer.visibility = View.VISIBLE
            txtReviewInlineStatus.visibility = View.GONE
            updateConversationRouteStatus()
            Log.d("GHALBIT-REVIEW-LAYOUT", "main composer restored")
        }
        ViewCompat.requestApplyInsets(chatRoot)
    }

    private fun applyCompactRouteStatus() {
        val compactText =
            when {
                !OnlineFallbackTransport.isConfigured() -> "RELAY belum siap"
                lastRuntimeSnapshot.state == RuntimeUiState.OFFLINE_PENDING -> "PENDING"
                lastRuntimeSnapshot.state == RuntimeUiState.WEAK_SIGNAL -> "MESH lemah"
                OnlinePresenceManager.getOnlineRoute(this, peerGlobalId.orEmpty()) != null -> "RELAY siap"
                peerIp.isNotBlank() -> "MESH aktif"
                else -> "Menunggu jalur"
            }
        txtRouteHealthStatus.text = appendPreparedRouteLabel(compactText)
        Log.d("GHALBIT-REVIEW-LAYOUT", "route compact mode")
    }

    private fun updateReviewInlineStatus(snapshot: RuntimeUiSnapshot) {
        if (reviewPanel.visibility != View.VISIBLE) {
            txtReviewInlineStatus.visibility = View.GONE
            return
        }
        val inlineText =
            when {
                !OnlineFallbackTransport.isConfigured() -> "Relay belum dikonfigurasi"
                snapshot.state == RuntimeUiState.OFFLINE_PENDING -> "Menunggu koneksi"
                snapshot.state == RuntimeUiState.INTERNET_FALLBACK -> "Relay internet aktif"
                snapshot.state == RuntimeUiState.WEAK_SIGNAL -> "Jalur mesh sedang lemah"
                OnlinePresenceManager.getOnlineRoute(this, peerGlobalId.orEmpty()) != null -> "Penerima siap via relay"
                peerIp.isNotBlank() -> "Jalur mesh tersedia"
                else -> "Menunggu penerima online"
            }
        txtReviewInlineStatus.text = inlineText
        txtReviewInlineStatus.visibility = View.VISIBLE
        Log.d("GHALBIT-REVIEW-LAYOUT", "inline network status shown")
    }

    private fun applyReviewResponsiveLayout(insets: WindowInsetsCompat? = ViewCompat.getRootWindowInsets(chatRoot)) {
        val screenHeight = resources.displayMetrics.heightPixels
        val imeVisible = insets?.isVisible(WindowInsetsCompat.Type.ime()) == true
        val imeInsets = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        val navInsets = insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        val previewHeight =
            if (imeVisible) {
                (screenHeight * 0.28f).toInt()
            } else {
                (screenHeight * 0.38f).toInt()
            }.coerceIn(dp(120), dp(280))
        imgReviewPreview.layoutParams =
            imgReviewPreview.layoutParams.apply {
                height = previewHeight
            }
        edtReviewCaption.maxLines = if (imeVisible) 2 else 4
        edtReviewCaption.minLines = if (imeVisible) 1 else 2
        reviewContentScroll.layoutParams =
            reviewContentScroll.layoutParams.apply {
                height =
                    if (imeVisible) {
                        (screenHeight * 0.42f).toInt().coerceAtLeast(dp(180))
                    } else {
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    }
            }
        reviewContentScroll.requestLayout()
        reviewPanel.updatePadding(bottom = maxOf(navInsets, dp(8)))
        if (imeVisible) {
            Log.d("GHALBIT-REVIEW-LAYOUT", "ime inset applied")
            Log.d("GHALBIT-REVIEW-LAYOUT", "compact mode active")
        }
        Log.d("GHALBIT-REVIEW-LAYOUT", "preview resized")
        Log.d("GHALBIT-REVIEW-LAYOUT", "sticky action bar ok")
        Log.d("GHALBIT-REVIEW-LAYOUT", "action bar visible")
        Log.d("GHALBIT-REVIEW-LAYOUT", "send button visible")
        Log.d("GHALBIT-REVIEW-LAYOUT", "compact buttons enabled")
        Log.d("GHALBIT-UI-PERF", "delivery indicator lightweight")
        Log.d("GHALBIT-UI-PERF", "skipped relayout")
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun disableChatButtons() {
        btnSend.isEnabled = false
        btnAttach.isEnabled = false
        btnCamera.isEnabled = false
        btnVoice.isEnabled = false
        btnRetryFailed.isEnabled = false
    }

    private fun startRecordingStatusTicker() {
        recordingStatusJob?.cancel()
        recordingStatusJob =
            lifecycleScope.launch {
                while (isRecording) {
                    val elapsedMs =
                        System.currentTimeMillis() - recordStartAt

                    txtChatStatus.text =
                        "${getString(R.string.chat_recording)} ${formatDuration(elapsedMs)}"

                    delay(250)
                }
            }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val conversationId = intent.getStringExtra(GhalbitDeepLinkRouter.EXTRA_CONVERSATION_ID).orEmpty()
        if (conversationId.isNotBlank()) {
            GhalbitDeepLinkRouter.logChatOpen(conversationId)
            Log.d("GHALBIT-NOTIFY", "message clicked id=${intent.getStringExtra(GhalbitDeepLinkRouter.EXTRA_MESSAGE_ID).orEmpty()}")
        }
    }

    private fun buildVoiceLabel(durationMs: Long): String {
        return "${getString(R.string.chat_voice_label)} (${formatDuration(durationMs)})"
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds =
            (durationMs / 1000L).coerceAtLeast(0L)

        val minutes =
            totalSeconds / 60L

        val seconds =
            totalSeconds % 60L

        return "%02d:%02d".format(minutes, seconds)
    }

    private fun openTextReview() {
        val message = edtMessage.text.toString().trim()

        if (message.isEmpty()) {
            UiFeedbackManager.showToast(
                this,
                getString(R.string.message_empty)
            )
            return
        }

        if (message.length > MAX_MESSAGE_LENGTH) {
            txtChatStatus.text = getString(R.string.message_too_long)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.message_too_long)
            )
            return
        }

        lifecycleScope.launch {
            val draftId = "DRAFT-${System.currentTimeMillis()}"
            withContext(Dispatchers.IO) {
                draftDb.draftMessageDao().findLatestDraft(peerName)
                    ?.let { existing -> draftDb.draftMessageDao().findAttachment(existing.draftId) }
                    ?.let { DraftAttachmentStore.remove(it.filePath) }
                draftDb.draftMessageDao().replaceDraft(
                    DraftMessageEntity(
                        draftId = draftId,
                        chatId = peerName,
                        draftType = ChatDeliveryState.DRAFT_TEXT.dbValue,
                        content = message,
                        status = ChatDeliveryState.REVIEW_READY.dbValue,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    ),
                    attachment = null
                )
            }
            currentDraftId = draftId
            currentReviewState = ReviewSendState.REVIEW_READY
            Log.d("GHALBIT-DRAFT", "created type=TEXT")
            Log.d("GHALBIT-SAFE-SEND", "draft created")
            Log.d("GHALBIT-SAFE-SEND", "review required")
            Log.d("GHALBIT-REVIEW", "open type=TEXT")
            renderDraftReview()
        }
    }

    private fun confirmCurrentDraft() {
        lifecycleScope.launch {
            val draft = withContext(Dispatchers.IO) { loadCurrentDraft() } ?: return@launch
            currentReviewState = ReviewSendState.SEND_CONFIRMED
            Log.d("GHALBIT-REVIEW", "confirmed id=${draft.draftId}")
            Log.d("GHALBIT-SAFE-SEND", "confirmed")
            when (draft.draftType) {
                ChatDeliveryState.DRAFT_TEXT.dbValue -> sendConfirmedText(edtReviewCaption.text.toString().trim())
                ChatDeliveryState.DRAFT_MEDIA.dbValue,
                ChatDeliveryState.DRAFT_FILE.dbValue -> {
                    val attachment = draft.attachment ?: return@launch
                    val updatedDraft = draft.copy(content = edtReviewCaption.text.toString().trim())
                    confirmAttachmentDraftSend(updatedDraft, attachment)
                }
            }
        }
    }

    private suspend fun loadCurrentDraft(): DraftMessage? {
        val entity = draftDb.draftMessageDao().findLatestDraft(peerName) ?: return null
        val attachmentEntity = draftDb.draftMessageDao().findAttachment(entity.draftId)
        return DraftMessage(
            draftId = entity.draftId,
            chatId = entity.chatId,
            draftType = entity.draftType,
            content = entity.content,
            status = entity.status,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            attachment = attachmentEntity?.let {
                DraftAttachment(
                    draftId = it.draftId,
                    contentType = it.contentType,
                    filePath = it.filePath,
                    displayName = it.displayName,
                    mimeType = it.mimeType,
                    fileSize = it.fileSize,
                    warning = it.warning
                )
            }
        )
    }

    private fun renderDraftReview() {
        lifecycleScope.launch {
            val draft = withContext(Dispatchers.IO) { loadCurrentDraft() } ?: run {
                reviewPanel.visibility = View.GONE
                setReviewMode(false)
                return@launch
            }
            currentDraftId = draft.draftId
            reviewPanel.visibility = View.VISIBLE
            setReviewMode(true)
            txtReviewTitle.text =
                when (draft.draftType) {
                    ChatDeliveryState.DRAFT_TEXT.dbValue -> "Tinjau pesan"
                    ChatDeliveryState.DRAFT_MEDIA.dbValue -> "Tinjau gambar"
                    else -> "Tinjau file"
                }
            edtReviewCaption.setText(draft.content)
            val routeHint =
                when {
                    OnlinePresenceManager.getOnlineRoute(this@ChatActivity, peerGlobalId.orEmpty()) != null -> "Relay"
                    peerIp.isNotBlank() -> "Mesh"
                    else -> "Pending"
                }
            val attachment = draft.attachment
            txtReviewMeta.text =
                if (attachment == null) {
                    "Target: $peerName"
                } else {
                    buildString {
                        append(attachment.displayName)
                        append(" • ")
                        append(formatFileSize(attachment.fileSize))
                        append(" • ")
                        append(attachment.mimeType.ifBlank { attachment.contentType })
                        append(" • ")
                        append(routeHint)
                        attachment.warning?.let {
                            append("\n")
                            append(it)
                        }
                    }
                }
            btnReviewReplace.visibility = if (attachment == null) View.GONE else View.VISIBLE
            if (attachment?.contentType == "IMAGE") {
                imgReviewPreview.visibility = View.VISIBLE
                val bitmap = withContext(Dispatchers.IO) { MediaPreviewLoader.loadImageThumbnail(attachment.filePath) }
                imgReviewPreview.setImageBitmap(bitmap)
                Log.d("GHALBIT-REVIEW", "thumbnail ready")
            } else {
                imgReviewPreview.setImageDrawable(null)
                imgReviewPreview.visibility = View.GONE
            }
            updateReviewInlineStatus(lastRuntimeSnapshot)
            applyReviewResponsiveLayout()
        }
    }

    private fun editCurrentDraft() {
        lifecycleScope.launch {
            val draft = withContext(Dispatchers.IO) { loadCurrentDraft() } ?: return@launch
            currentReviewState = ReviewSendState.EDITING_DRAFT
            if (draft.attachment != null) {
                edtReviewCaption.requestFocus()
                applyReviewResponsiveLayout()
            } else {
                edtMessage.setText(edtReviewCaption.text.toString())
                edtMessage.requestFocus()
                reviewPanel.visibility = View.GONE
                setReviewMode(false)
            }
            txtChatStatus.text = "Draft siap diedit"
            Log.d("GHALBIT-REVIEW", "edited messageId=${draft.draftId}")
        }
    }

    private fun replaceCurrentDraftAttachment() {
        val draftId = currentDraftId ?: return
        lifecycleScope.launch {
            val draft = withContext(Dispatchers.IO) { loadCurrentDraft() } ?: return@launch
            if (draft.attachment == null) return@launch
            edtMessage.setText(edtReviewCaption.text.toString())
            withContext(Dispatchers.IO) {
                DraftAttachmentStore.remove(draft.attachment.filePath)
                draftDb.draftMessageDao().deleteDraft(draftId)
            }
            Log.d("GHALBIT-REVIEW", "file replaced id=$draftId")
            Log.d("GHALBIT-FILE-DRAFT", "replaced")
            filePickerLauncher.launch("*/*")
        }
    }

    private fun cancelCurrentDraft(cleanOnly: Boolean = false, keepAttachment: Boolean = false) {
        val draftId = currentDraftId ?: return
        lifecycleScope.launch {
            val draft = withContext(Dispatchers.IO) { loadCurrentDraft() }
            withContext(Dispatchers.IO) {
                if (!keepAttachment) {
                    draft?.attachment?.let { DraftAttachmentStore.remove(it.filePath) }
                }
                draftDb.draftMessageDao().deleteDraft(draftId)
            }
            currentDraftId = null
            currentReviewState = ReviewSendState.SEND_CANCELLED
            reviewPanel.visibility = View.GONE
            setReviewMode(false)
            if (!cleanOnly) {
                txtChatStatus.text = "Pesan dibatalkan"
                runtimeSoftBanner.showMessage(
                    key = "draft:cancel:$draftId",
                    title = "Pesan dibatalkan",
                    detail = "Draft dihapus sebelum masuk jalur kirim.",
                    priority = 2,
                    durationMs = 1500L
                )
                Log.d("GHALBIT-DRAFT", "cancelled id=$draftId")
                Log.d("GHALBIT-SAFE-SEND", "cancelled before delivery")
                Log.d("GHALBIT-REVIEW", "cancelled id=$draftId")
            } else {
                Log.d("GHALBIT-DRAFT", "cleaned id=$draftId")
            }
        }
    }

    private suspend fun restoreDraftIfNeeded() {
        val draft = withContext(Dispatchers.IO) { loadCurrentDraft() } ?: return
        currentDraftId = draft.draftId
        Log.d("GHALBIT-DRAFT", "restored id=${draft.draftId}")
        renderDraftReview()
    }

    private fun sendConfirmedText(message: String) {
        if (message.isBlank()) {
            UiFeedbackManager.showToast(this, getString(R.string.message_empty))
            return
        }

        if (peerIp.isEmpty() && peerGlobalId.isNullOrBlank()) {
            txtChatStatus.text = getString(R.string.peer_ip_empty)
            UiFeedbackManager.showToast(this, getString(R.string.peer_ip_empty))
            return
        }

        lifecycleScope.launch {
            btnSend.isEnabled = false
            btnAttach.isEnabled = false
            btnCamera.isEnabled = false
            btnVoice.isEnabled = false
            btnRetryFailed.isEnabled = false
            btnSend.text = "SENDING..."
            txtChatStatus.text = "Mengirim..."
            runtimeSoftBanner.showMessage(
                key = "chat:send:$peerName",
                title = "Mengirim...",
                detail = "Pesan sedang keluar ke jalur terbaik.",
                priority = 3,
                durationMs = 1500L,
                miniStatus = "Mencari jalur terbaik..."
            )

            try {
                val ownershipHint = withContext(Dispatchers.IO) { refreshConversationOwnershipHint() }
                val request =
                    ChatDeliveryManager.createRequest(
                        context = this@ChatActivity,
                        keyStore = keyStore,
                        chatId = peerName,
                        peerIp = peerIp,
                        message = message,
                        peerGlobalId = ownershipHint.globalId,
                        peerPublicKey = ownershipHint.publicKey,
                        peerWalletAddress = ownershipHint.walletAddress,
                        peerDisplayName = ownershipHint.canonicalDisplayName
                    )

                ChatRetryMetadataRegistry.put(
                    request.packetId,
                    ChatRetryMetadata(
                        peerGlobalId = request.peerGlobalId,
                        peerPublicKey = request.peerPublicKey,
                        peerWalletAddress = request.peerWalletAddress,
                        peerDisplayName = request.peerDisplayName
                    )
                )

                withContext(Dispatchers.IO) {
                    ChatDeliveryManager.sendTextMessage(
                        context = this@ChatActivity,
                        keyStore = keyStore,
                        request = request
                    )
                }
                txtChatStatus.text = "Mengirim..."
                txtRouteHealthStatus.text = "Menyambungkan ulang"
                renderHistory()
                edtMessage.setText("")
                cancelCurrentDraft(cleanOnly = true)
                Log.d("GHALBIT-SAFE-SEND", "delivery started")
                Log.d("GHALBIT-CHAT-UI", "queued packetId=${request.packetId} messageId=${request.messageId}")
            } catch (e: Exception) {
                MeshLogger.e("CHAT", "Send failed", e)
                txtChatStatus.text = getString(R.string.send_failed)
                UiFeedbackManager.showToast(this@ChatActivity, getString(R.string.send_failed))
            } finally {
                restoreChatButtons()
                btnSend.text = getString(R.string.send)
            }
        }
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", size / (1024f * 1024f))
            size >= 1024L -> String.format(Locale.US, "%.1f KB", size / 1024f)
            else -> "$size B"
        }
    }

    private fun retryLastFailedMessage() {
        if (peerIp.isEmpty()) {
            txtChatStatus.text = getString(R.string.peer_ip_empty)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.peer_ip_empty)
            )
            return
        }

        lifecycleScope.launch {
            btnSend.isEnabled = false
            btnAttach.isEnabled = false
            btnCamera.isEnabled = false
            btnVoice.isEnabled = false
            btnRetryFailed.isEnabled = false
            btnRetryFailed.text = "RETRYING..."
            txtChatStatus.text = getString(R.string.chat_retrying)

            try {
                val failedMessage =
                    withContext(Dispatchers.IO) {
                        chatDb.chatDao().getLastFailedMessage(peerName)
                    }

                if (failedMessage == null) {
                    UiFeedbackManager.showToast(
                        this@ChatActivity,
                        getString(R.string.no_failed_message)
                    )
                    txtChatStatus.text = getString(R.string.no_failed_message)
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    ChatDeliveryManager.retryMessage(this@ChatActivity, failedMessage.packetId.ifBlank { failedMessage.id.toString() })
                    ChatDeliveryManager.retryPendingForChat(this@ChatActivity, peerName)
                }
                txtChatStatus.text = "Mencoba ulang"
                runtimeSoftBanner.showMessage(
                    key = "chat:retry:$peerName",
                    title = "Mencoba ulang",
                    detail = "Pesan akan dikirim lagi saat jalur siap.",
                    priority = 4,
                    durationMs = 2000L,
                    miniStatus = "Menunggu koneksi"
                )
                renderHistory()
            } catch (e: Exception) {
                MeshLogger.e("CHAT", "Retry failed", e)
                txtChatStatus.text = getString(R.string.send_failed)
                UiFeedbackManager.showToast(
                    this@ChatActivity,
                    getString(R.string.send_failed)
                )
            } finally {
                restoreChatButtons()
                btnRetryFailed.text = getString(R.string.retry_failed)
            }
        }
    }

    private fun updateConversationRouteStatus() {
        val keepAliveState = ConversationKeepAliveManager.snapshot(peerName)
        val statusText =
            when {
                keepAliveState != null -> {
                    val latencyText =
                        if (keepAliveState.latencyMs >= 0L) " • ${keepAliveState.latencyMs}ms" else ""
                    "${keepAliveState.routeHealth.label}$latencyText"
                }
                OnlinePresenceManager.getOnlineRoute(this, peerGlobalId.orEmpty()) != null -> "Online internet"
                PendingMessageStore.countForChat(this, peerName) > 0 -> "Offline / pending"
                else -> "Reconnecting"
            }
        lastRouteStatusText = statusText
        if (reviewPanel.visibility == View.VISIBLE) {
            applyCompactRouteStatus()
        } else {
            txtRouteHealthStatus.text = appendPreparedRouteLabel(statusText)
        }
    }

    private fun appendPreparedRouteLabel(base: String): String {
        return if (lastPreparedRouteLabel.isBlank()) base else "$base | $lastPreparedRouteLabel"
    }

    private fun observeRuntimeUiState() {
        lifecycleScope.launch {
            RuntimeUiStateManager.stateFlow.collectLatest { snapshot ->
                lastRuntimeSnapshot = snapshot
                runtimeLoadingOverlay.render(snapshot)
                if (reviewPanel.visibility == View.VISIBLE) {
                    Log.d("GHALBIT-REVIEW-LAYOUT", "banner suppressed during review")
                    updateReviewInlineStatus(snapshot)
                } else {
                    runtimeSoftBanner.render(snapshot)
                }
                txtChatStatus.text = snapshot.detail
                btnSend.isEnabled = !snapshot.actionsLocked
                btnCall.isEnabled = !snapshot.actionsLocked
                btnAttach.isEnabled = !snapshot.actionsLocked
                btnCamera.isEnabled = !snapshot.actionsLocked
                btnRetryFailed.isEnabled = !snapshot.actionsLocked
                btnReviewEdit.isEnabled = !snapshot.actionsLocked
                btnReviewReplace.isEnabled = !snapshot.actionsLocked
                btnReviewCancel.isEnabled = !snapshot.actionsLocked
                btnReviewConfirm.isEnabled = !snapshot.actionsLocked
                Log.d("GHALBIT-UX", "chat state=${snapshot.state} peer=$peerName")
            }
        }
    }

    private fun resolveAttachmentContentType(uri: Uri): String {
        val mimeType =
            contentResolver.getType(uri).orEmpty()

        return if (mimeType.startsWith("image/")) {
            "IMAGE"
        } else {
            "FILE"
        }
    }

    private fun readDisplayName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index) ?: "file"
            }
        }

        return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    }

    private fun showMessageActions(message: ChatMessage) {
        val state = ChatDeliveryState.fromDb(message.status)
        val items =
            buildList {
                if (message.isSent && !message.status.uppercase(Locale.ROOT).contains("DELETED")) {
                    add("Edit")
                }
                add("Hapus untuk saya")
                if (message.isSent && !message.status.uppercase(Locale.ROOT).contains("DELETED")) {
                    add("Hapus untuk semua")
                }
                add("Info pengiriman")
                if (state == ChatDeliveryState.FAILED_FINAL) {
                    add("Kirim ulang")
                }
                if (message.contentType == "TEXT" || message.contentType == "SOS") {
                    add("Salin teks")
                }
                if (message.contentType == "AUDIO" || message.contentType == "IMAGE" || message.contentType == "FILE") {
                    add("Buka file")
                    add(getString(R.string.chat_action_save))
                }
                add(getString(R.string.chat_action_share))
            }

        Log.d("GHALBIT-MESSAGE-MENU", "open id=${message.packetId}")

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.chat_action_title))
            .setItems(items.toTypedArray()) { _, which ->
                val action = items[which]
                Log.d("GHALBIT-MESSAGE-MENU", "action=$action")
                when (action) {
                    "Edit" -> startEditMessage(message)
                    "Hapus untuk saya" -> deleteMessageForMe(message)
                    "Hapus untuk semua" -> deleteMessageForEveryone(message)
                    "Info pengiriman" -> showDeliveryInfo(message)
                    "Kirim ulang" -> retryMessageFromMenu(message)
                    "Salin teks" -> copyMessageText(message)
                    "Buka file" -> handleMessageClick(message)
                    getString(R.string.chat_action_save) -> saveMessageToDevice(message)
                    getString(R.string.chat_action_share) -> shareMessage(message)
                }
            }
            .show()
    }

    private fun startEditMessage(message: ChatMessage) {
        if (!message.isSent || message.status.uppercase(Locale.ROOT).contains("DELETED")) {
            return
        }
        edtMessage.setText(if (message.status.uppercase(Locale.ROOT).contains("DELETED")) "" else message.content)
        edtMessage.requestFocus()
        AlertDialog.Builder(this)
            .setTitle("Edit pesan")
            .setPositiveButton("Simpan") { _, _ ->
                val newContent = edtMessage.text.toString().trim()
                if (newContent.isNotBlank()) {
                    ChatDeliveryManager.editMessage(this, message.packetId, peerName, newContent, peerGlobalId)
                    runtimeSoftBanner.showMessage(
                        key = "message:edited:${message.packetId}",
                        title = "Pesan diedit",
                        detail = "Perubahan disimpan pada percakapan.",
                        priority = 2,
                        durationMs = 1500L
                    )
                    lifecycleScope.launch {
                        delay(180L)
                        renderHistory()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteMessageForMe(message: ChatMessage) {
        ChatDeliveryManager.deleteMessageForMe(this, message.packetId)
        runtimeSoftBanner.showMessage(
            key = "message:delete:me:${message.packetId}",
            title = "Pesan dihapus",
            detail = "Hanya hilang dari perangkat ini.",
            priority = 2,
            durationMs = 1500L
        )
        lifecycleScope.launch {
            delay(180L)
            renderHistory()
        }
    }

    private fun deleteMessageForEveryone(message: ChatMessage) {
        ChatDeliveryManager.deleteMessageForEveryone(this, message.packetId, peerName, peerGlobalId)
        runtimeSoftBanner.showMessage(
            key = "message:delete:all:${message.packetId}",
            title = "Menghapus pesan",
            detail = "Permintaan hapus sedang dikirim.",
            priority = 3,
            durationMs = 1500L
        )
        lifecycleScope.launch {
            delay(180L)
            renderHistory()
        }
    }

    private fun showDeliveryInfo(message: ChatMessage) {
        AlertDialog.Builder(this)
            .setTitle("Info pengiriman")
            .setMessage(
                buildString {
                    append("Status: ")
                    append(ChatDeliveryState.fromDb(message.status).userLabel)
                    append("\n")
                    append("Packet: ")
                    append(message.packetId)
                    append("\n")
                    append("Waktu: ")
                    append(SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID")).format(Date(message.timestamp)))
                }
            )
            .setPositiveButton("Tutup", null)
            .show()
    }

    private fun retryMessageFromMenu(message: ChatMessage) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                ChatDeliveryManager.retryMessage(this@ChatActivity, message.packetId)
            }
            runtimeSoftBanner.showMessage(
                key = "message:retry:${message.packetId}",
                title = "Mencoba ulang",
                detail = "Pesan akan dicoba lagi saat jalur siap.",
                priority = 3,
                durationMs = 1500L
            )
            renderHistory()
        }
    }

    private fun copyMessageText(message: ChatMessage) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("chat", message.content))
        runtimeSoftBanner.showMessage(
            key = "message:copy:${message.packetId}",
            title = "Teks disalin",
            detail = "Isi pesan siap dipakai.",
            priority = 1,
            durationMs = 1200L
        )
    }

    private fun shareMessage(message: ChatMessage) {
        when (message.contentType) {
            "AUDIO", "IMAGE", "FILE" -> shareFileMessage(message)
            else -> shareTextMessage(message)
        }
    }

    private fun shareTextMessage(message: ChatMessage) {
        val text =
            "${message.senderName}: ${message.content}"

        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                getString(R.string.chat_share_via)
            )
        )
    }

    private fun shareFileMessage(message: ChatMessage) {
        val file = resolveMessageFile(message) ?: run {
            txtChatStatus.text = getString(R.string.chat_file_missing)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.chat_file_missing)
            )
            return
        }

        try {
            val uri =
                FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )

            val mimeType =
                resolveMessageMimeType(message, file)

            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, message.content)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    getString(R.string.chat_share_via)
                )
            )
        } catch (e: Exception) {
            MeshLogger.e("CHAT", "Share file failed", e)
            txtChatStatus.text = getString(R.string.chat_share_failed)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.chat_share_failed)
            )
        }
    }

    private fun saveMessageToDevice(message: ChatMessage) {
        val file = resolveMessageFile(message) ?: run {
            txtChatStatus.text = getString(R.string.chat_file_missing)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.chat_file_missing)
            )
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val result =
                runCatching {
                    saveFileToMediaStore(message, file)
                }

            runOnUiThread {
                if (result.isSuccess) {
                    txtChatStatus.text = getString(R.string.chat_saved_success)
                    UiFeedbackManager.showToast(
                        this@ChatActivity,
                        getString(R.string.chat_saved_success)
                    )
                } else {
                    txtChatStatus.text = getString(R.string.chat_saved_failed)
                    UiFeedbackManager.showToast(
                        this@ChatActivity,
                        getString(R.string.chat_saved_failed)
                    )
                }
            }
        }
    }

    private fun saveFileToMediaStore(
        message: ChatMessage,
        file: File
    ) {
        val mimeType =
            resolveMessageMimeType(message, file)
        val resolver = contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, resolveRelativePath(message))
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

        val collection =
            when (message.contentType) {
                "IMAGE" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                "AUDIO" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
            }

        val targetUri =
            resolver.insert(collection, values)
                ?: throw IllegalStateException("Insert media store failed")

        resolver.openOutputStream(targetUri)?.use { output ->
            FileInputStream(file).use { input ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Open output stream failed")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(targetUri, values, null, null)
        }
    }

    private fun resolveRelativePath(message: ChatMessage): String {
        return when (message.contentType) {
            "IMAGE" -> Environment.DIRECTORY_PICTURES + "/GhalbitMesh"
            "AUDIO" -> Environment.DIRECTORY_MUSIC + "/GhalbitMesh"
            else -> Environment.DIRECTORY_DOWNLOADS + "/GhalbitMesh"
        }
    }

    private fun resolveMessageFile(message: ChatMessage): File? {
        val path = message.filePath ?: return null
        val file = File(path)
        return file.takeIf { it.exists() }
    }

    private fun resolveMessageMimeType(
        message: ChatMessage,
        file: File
    ): String {
        return when (message.contentType) {
            "IMAGE" -> "image/jpeg"
            "AUDIO" -> "audio/mp4"
            else -> {
                MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(file.extension.lowercase())
                    ?: "application/octet-stream"
            }
        }
    }

    private suspend fun renderHistory(
        systemLine: String? = null
    ) {
        val shouldAutoScroll = shouldAutoScrollMessages()
        val historyBundle =
            withContext(Dispatchers.IO) {
                val messages = chatDb.chatDao().getMessages(peerName)
                val resolvedProfile =
                    ProfileRepository.getResolvedContact(
                        context = this@ChatActivity,
                        globalId = activeConversationHint?.globalId ?: peerGlobalId,
                        chatId = peerName,
                        fallbackDisplayName = activeConversationHint?.canonicalDisplayName ?: peerDisplayName ?: peerName,
                        publicKeyHash = activeConversationHint?.publicKey?.let { com.ghalbitnet.meshx2.call.CallManager.publicKeyHash(it) },
                        routeHint = activeConversationHint?.lastKnownIp ?: peerIp
                    )
                Pair(messages, resolvedProfile)
            }
        val messages = ChatTimelineOptimizer.optimize(this, historyBundle.first)
        val resolvedProfile = historyBundle.second
        val displayName = resolvedProfile.primaryName
        val publicName = resolvedProfile.displayName
        peerDisplayName = publicName

        val headerName =
            IdentityDisplayFormatter.primaryLabel(
                canonicalDisplayName = displayName,
                walletAddress = activeConversationHint?.walletAddress ?: peerWalletAddress,
                globalId = activeConversationHint?.globalId ?: peerGlobalId,
                publicKey = activeConversationHint?.publicKey ?: peerPublicKey,
                legacyName = peerName,
                ipAddress = peerIp
            )
        val headerHint =
            IdentityDisplayFormatter.secondaryLabel(
                primaryLabel = headerName,
                legacyName = peerName,
                walletAddress = activeConversationHint?.walletAddress ?: peerWalletAddress,
                globalId = activeConversationHint?.globalId ?: peerGlobalId,
                publicKey = activeConversationHint?.publicKey ?: peerPublicKey,
                ipAddress = peerIp
            )

        txtChat.text =
            if (systemLine == null) {
                buildString {
                    append("Chat with ")
                    append(headerName)
                    if (displayName != publicName && publicName.isNotBlank()) {
                        append("\nPublik: ")
                        append(publicName)
                    }
                    headerHint?.let {
                        append("\n")
                        append(it)
                    }
                }
            } else {
                buildString {
                    append("Chat with ")
                    append(headerName)
                    if (displayName != publicName && publicName.isNotBlank()) {
                        append("\nPublik: ")
                        append(publicName)
                    }
                    headerHint?.let {
                        append("\n")
                        append(it)
                    }
                    append("\n")
                    append(systemLine)
                }
            }

        chatAdapter.submitMessages(messages)

        if (messages.isNotEmpty() && shouldAutoScroll) {
            rvMessages.scrollToPosition(messages.lastIndex)
        }
    }

    private fun shouldAutoScrollMessages(): Boolean {
        val layoutManager = rvMessages.layoutManager as? LinearLayoutManager ?: return true
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        val count = chatAdapter.itemCount
        return count <= 2 || lastVisible >= count - 3
    }

    private fun refreshConversationOwnershipHint(
        ipHint: String? = peerIp,
        globalIdHint: String? = peerGlobalId,
        publicKeyHint: String? = peerPublicKey,
        walletAddressHint: String? = peerWalletAddress,
        displayNameHint: String? = peerDisplayName
    ): ConversationOwnershipHint {
        val resolved =
            CentralIdentityResolver.resolve(
                context = this,
                legacyChatId = peerName,
                peerName = peerName,
                peerIp = ipHint,
                globalIdHint = globalIdHint,
                publicKeyHint = publicKeyHint,
                walletAddressHint = walletAddressHint,
                displayNameHint = displayNameHint
            )

        val hint =
            ConversationOwnershipHint(
                legacyChatId = peerName,
                globalId = resolved.globalId,
                publicKey = resolved.publicKey,
                walletAddress = resolved.walletAddress,
                canonicalDisplayName = resolved.displayName,
                lastKnownIp = resolved.peerIp.ifBlank { null },
                updatedAt = resolved.resolvedAt
            )

        activeConversationHint = hint
        peerGlobalId = hint.globalId
        peerPublicKey = hint.publicKey
        peerWalletAddress = hint.walletAddress
        peerDisplayName = hint.canonicalDisplayName ?: peerDisplayName
        peerIp = hint.lastKnownIp?.takeIf { it.isNotBlank() } ?: peerIp

        return hint
    }

}
