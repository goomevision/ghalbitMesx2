package com.ghalbitnet.meshx2.call

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ghalbitnet.meshx2.MainActivity
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.chat.ChatDatabase
import com.ghalbitnet.meshx2.chat.ChatMessage
import com.ghalbitnet.meshx2.chat.ConversationKeepAliveManager
import com.ghalbitnet.meshx2.chat.ConversationOwnershipHint
import com.ghalbitnet.meshx2.core.log.MeshLogger
import com.ghalbitnet.meshx2.core.utils.AppNotificationManager
import com.ghalbitnet.meshx2.core.utils.GhalbitDeepLinkRouter
import com.ghalbitnet.meshx2.core.utils.UiFeedbackManager
import com.ghalbitnet.meshx2.file.FileTransferManager
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.identity.CentralIdentityResolver
import com.ghalbitnet.meshx2.identity.IdentityDisplayFormatter
import com.ghalbitnet.meshx2.security.KeyStoreManager
import com.ghalbitnet.meshx2.profile.ContactNameCardActivity
import com.ghalbitnet.meshx2.profile.ProfileRepository
import com.ghalbitnet.meshx2.profile.ProfileSyncManager
import com.ghalbitnet.meshx2.online.PreparedRouteManager
import com.ghalbitnet.meshx2.online.RelayConfigValidation
import com.ghalbitnet.meshx2.online.RelayConfigValidator
import com.ghalbitnet.meshx2.routing.CallRouteDiscoveryManager
import com.ghalbitnet.meshx2.routing.RouteProbeValidator
import com.ghalbitnet.meshx2.routing.TriplePathRoutePolicy
import com.ghalbitnet.meshx2.settings.CommunicationSettingsManager
import com.ghalbitnet.meshx2.settings.DeveloperModeManager
import com.ghalbitnet.meshx2.ui.ActionDebounceManager
import com.ghalbitnet.meshx2.ui.CallSearchingToneManager
import com.ghalbitnet.meshx2.ui.RouteSearchingAnimator
import com.ghalbitnet.meshx2.ui.RuntimeLoadingOverlay
import com.ghalbitnet.meshx2.ui.RuntimeSoftBannerManager
import com.ghalbitnet.meshx2.ui.RuntimeUiStateManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CallSessionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PEER_NAME = "peerName"
        const val EXTRA_PEER_IP = "peerIp"
        const val EXTRA_CALL_ID = "callId"
        const val EXTRA_INCOMING = "incoming"
        const val EXTRA_PEER_GLOBAL_ID = "peerGlobalId"
        const val EXTRA_PEER_PUBLIC_KEY = "peerPublicKey"
        const val EXTRA_PEER_WALLET_ADDRESS = "peerWalletAddress"
        const val EXTRA_PEER_DISPLAY_NAME = "peerDisplayName"
        private const val MIN_VOICE_DURATION_MS = 700L
        private const val OUTGOING_TIMEOUT_MS = 20_000L
        private const val RINGING_TIMEOUT_MS = 30_000L
        private const val NO_AUDIO_TIMEOUT_MS = 10_000L

        fun createIntent(
            context: Context,
            peerName: String,
            peerIp: String,
            callId: String,
            incoming: Boolean,
            peerGlobalId: String? = null,
            peerPublicKey: String? = null,
            peerWalletAddress: String? = null,
            peerDisplayName: String? = null
        ): Intent {
            return Intent(context, CallSessionActivity::class.java).apply {
                putExtra(EXTRA_PEER_NAME, peerName)
                putExtra(EXTRA_PEER_IP, peerIp)
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_INCOMING, incoming)
                putExtra(EXTRA_PEER_GLOBAL_ID, peerGlobalId)
                putExtra(EXTRA_PEER_PUBLIC_KEY, peerPublicKey)
                putExtra(EXTRA_PEER_WALLET_ADDRESS, peerWalletAddress)
                putExtra(EXTRA_PEER_DISPLAY_NAME, peerDisplayName)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }

    private lateinit var txtCallTitle: TextView
    private lateinit var txtCallStatus: TextView
    private lateinit var btnAccept: Button
    private lateinit var btnReject: Button
    private lateinit var btnTalk: Button
    private lateinit var btnEnd: Button
    private lateinit var btnMute: Button
    private lateinit var btnSpeaker: Button
    private lateinit var btnVideo: Button
    private lateinit var runtimeLoadingOverlay: RuntimeLoadingOverlay
    private lateinit var runtimeSoftBanner: RuntimeSoftBannerManager

    private lateinit var keyStore: KeyStoreManager
    private lateinit var audioManager: AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var peerName: String = ""
    private var peerIp: String = ""
    private var callId: String = ""
    private var peerGlobalId: String? = null
    private var peerPublicKey: String? = null
    private var peerWalletAddress: String? = null
    private var peerDisplayName: String? = null
    private var incoming = false
    private var callOwnershipHint: ConversationOwnershipHint? = null
    private var peerEndpoint: CallPeerEndpoint? = null
    private var callSession: CallSession? = null
    private var callState: CallState = CallState.IDLE
    private var endSignalSent = false
    private var finishedSafely = false
    private var realtimeFailed = false
    private var callActionInFlight = false
    private var waitingForRoute = false
    private var speakerEnabled = true
    private var muted = false
    private var lastAudioPacketAt = 0L
    private var pendingVoiceStart = false
    private var videoActive = false
    private var mediaPlayer: MediaPlayer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var isRecording = false
    private var recordStartAt = 0L
    private var recordingStatusJob: Job? = null
    private var timeoutJob: Job? = null
    private var audioWatchdogJob: Job? = null
    private var routeRecoveryJob: Job? = null
    private var pendingVoiceHandshakeJob: Job? = null
    private var lastRelayValidation: RelayConfigValidation? = null
    private var currentVoiceMode: AdaptiveVoiceMode = AdaptiveVoiceMode.PTT_STORE_FORWARD
    private var lastVoiceModeSwitchAt = 0L
    private var preparedRouteStatusLabel: String = ""
    private lateinit var callSearchingToneManager: CallSearchingToneManager
    private lateinit var routeSearchingAnimator: RouteSearchingAnimator
    private val signalAckWaiters = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    private val fullDuplexEngine =
        FullDuplexCallEngine(
            sessionProvider = { callSession },
            endpointProvider = { peerEndpoint },
            onRealtimeFailure = { reason ->
                mainHandler.post {
                    realtimeFailed = true
                    btnTalk.visibility = View.VISIBLE
                    setCallStatus(reason)
                    UiFeedbackManager.showToast(this, reason)
                    Log.w("GHALBIT-CALL-RTC", reason)
                }
            }
        )
    private val voiceAudioEngine by lazy { VoiceAudioEngine(this, audioManager, fullDuplexEngine) }
    private val codecAdapter: CodecAdapter by lazy { SpeechOptimizedCodecAdapter() }
    private val voicePlaybackScheduler by lazy { VoicePlaybackScheduler() }
    private val adaptiveJitterBuffer by lazy { AdaptiveJitterBuffer() }
    private val bandwidthEstimator by lazy { BandwidthEstimator() }
    private val voiceCapacitorBuffer by lazy {
        VoiceCapacitorBuffer(
            callId = callId,
            senderGlobalId = GhalbitCallManager.localGlobalId().ifBlank { "unknown" }
        )
    }
    private val voiceChunkAssembler by lazy { VoiceChunkAssembler() }
    private val voicePreProcessor by lazy { VoicePreProcessor() }
    private val vadEngine by lazy { SimpleVadEngine() }
    private val speechPriorityQueue by lazy { SpeechPriorityQueue() }
    private val speechToTextEngine by lazy { SpeechToTextEngine(this) }
    private val aiVoicePlaybackEngine by lazy { AiVoicePlaybackEngine(this) }
    private val aiVoiceTranscriptAssembler by lazy { AiVoiceTranscriptAssembler() }
    private var lastBandwidthSnapshot: BandwidthSnapshot? = null
    private var lastReceivedVoiceSequence = -1
    private val voiceProbeManager by lazy {
        VoiceProbeManager(
            sendProbe = { type, _ ->
                val peer = peerEndpoint
                val localNodeId = MainActivity.myGlobalPeerId
                val localGlobalId = GlobalMeshIdentityManager.buildGlobalId(keyStore.publicKeyBase64)
                val localPublicKeyHash = CallManager.localPublicKeyHash(this)
                if (peer == null || localNodeId.isBlank()) {
                    false
                } else {
                    CallManager.sendCustomSignal(
                        context = this,
                        peer = peer,
                        type = type,
                        payload = CallManager.buildSignalPayload(callId, localNodeId, localGlobalId, localPublicKeyHash),
                        localNodeId = localNodeId
                    )
                }
            },
            awaitAck = { ackType ->
                val waiter = CompletableDeferred<Boolean>()
                signalAckWaiters[ackType] = waiter
                val result = withTimeoutOrNull(5_000L) { waiter.await() } == true
                signalAckWaiters.remove(ackType)
                result
            }
        )
    }

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Log.d("GHALBIT-PERMISSION", "mic granted")
                Log.d("GHALBIT-AUDIO", "permission granted")
                if (pendingVoiceStart) {
                    startTalkRecording()
                } else if (
                    callState == CallState.WAITING_FOR_AUDIO_PATH ||
                    callState == CallState.VOICE_HANDSHAKING ||
                    callState == CallState.CALL_CONNECTED_SIGNAL_ONLY ||
                    callState == CallState.CONNECTED
                ) {
                    beginVoiceActivation("permission_granted")
                }
            } else {
                Log.w("GHALBIT-PERMISSION", "mic denied")
                Log.w("GHALBIT-AUDIO", "permission denied")
                postUi {
                    updateState(CallState.PTT_FALLBACK, "Izin mikrofon belum diberikan")
                    UiFeedbackManager.showToast(this, getString(R.string.chat_voice_permission))
                }
            }
            pendingVoiceStart = false
        }

    private val packetReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val source = intent?.getStringExtra("source") ?: return
                val type = intent.getStringExtra("type") ?: return
                val payload = intent.getStringExtra("payload") ?: ""
                if (source != peerName) return

                when (type) {
                    CallManager.SIGNAL_CALL_ACCEPT -> handleCallAccepted(payload)
                    CallManager.SIGNAL_CALL_REJECT -> handleCallRejected(payload)
                    CallManager.SIGNAL_CALL_END -> handleCallEnded(payload)
                    CallManager.SIGNAL_CALL_BUSY -> handleCallBusy(payload)
                    CallManager.SIGNAL_CALL_AUDIO_FRAME -> handleIncomingAudioFrame(payload)
                    CallManager.SIGNAL_VOICE_PROBE -> replyVoiceAck(CallManager.SIGNAL_VOICE_PROBE_ACK)
                    CallManager.SIGNAL_VOICE_PROBE_ACK -> completeSignalAck(CallManager.SIGNAL_VOICE_PROBE_ACK)
                    CallManager.SIGNAL_VOICE_HELLO -> replyVoiceAck(CallManager.SIGNAL_VOICE_HELLO_ACK)
                    CallManager.SIGNAL_VOICE_HELLO_ACK -> completeSignalAck(CallManager.SIGNAL_VOICE_HELLO_ACK)
                    CallManager.SIGNAL_VOICE_TRANSPORT_PROBE -> replyVoiceAck(CallManager.SIGNAL_VOICE_TRANSPORT_ACK)
                    CallManager.SIGNAL_VOICE_TRANSPORT_ACK -> completeSignalAck(CallManager.SIGNAL_VOICE_TRANSPORT_ACK)
                    CallManager.SIGNAL_VOICE_STREAM_ACTIVE_ACK -> completeSignalAck(CallManager.SIGNAL_VOICE_STREAM_ACTIVE_ACK)
                    CallManager.SIGNAL_CALL_START,
                    CallManager.SIGNAL_CALL_INVITE -> handleIncomingCallRefresh(payload)
                }
            }
        }

    private val audioReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val source =
                    intent?.getStringExtra(FileTransferManager.EXTRA_AUDIO_SOURCE) ?: return
                if (source != peerName || !isPttFallbackState(callState)) {
                    return
                }

                val filePath =
                    intent.getStringExtra(FileTransferManager.EXTRA_AUDIO_FILE_PATH) ?: return
                val label =
                    intent.getStringExtra(FileTransferManager.EXTRA_AUDIO_LABEL)
                        ?: getString(R.string.chat_voice_label)
                playIncomingAudio(filePath, label)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_session)

        keyStore = KeyStoreManager(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Log.d("GHALBIT-VOICE-AUDIT", "start")
        Log.d(
            "GHALBIT-VOICE-AUDIT",
            "mic permission=${ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED}"
        )

        peerName = intent.getStringExtra(EXTRA_PEER_NAME) ?: "UNKNOWN"
        peerIp = intent.getStringExtra(EXTRA_PEER_IP) ?: ""
        callId = intent.getStringExtra(EXTRA_CALL_ID) ?: UUID.randomUUID().toString()
        peerGlobalId = intent.getStringExtra(EXTRA_PEER_GLOBAL_ID)
        peerPublicKey = intent.getStringExtra(EXTRA_PEER_PUBLIC_KEY)
        peerWalletAddress = intent.getStringExtra(EXTRA_PEER_WALLET_ADDRESS)
        peerDisplayName = intent.getStringExtra(EXTRA_PEER_DISPLAY_NAME)
        incoming = intent.getBooleanExtra(EXTRA_INCOMING, false)

        txtCallTitle = findViewById(R.id.txtCallTitle)
        txtCallStatus = findViewById(R.id.txtCallStatus)
        txtCallTitle.setOnClickListener {
            startActivity(
                ContactNameCardActivity.createIntent(
                    context = this,
                    globalId = peerGlobalId,
                    chatId = peerName,
                    fallbackName = peerDisplayName ?: peerName,
                    publicKeyHash = CallManager.publicKeyHash(peerPublicKey),
                    routeHint = peerEndpoint?.routeHint ?: peerIp
                )
            )
            Log.d("GHALBIT-CARD", "opened from call")
        }
        btnAccept = findViewById(R.id.btnAcceptCall)
        btnReject = findViewById(R.id.btnRejectCall)
        btnTalk = findViewById(R.id.btnTalkCall)
        btnEnd = findViewById(R.id.btnEndCall)
        btnMute = findViewById(R.id.btnMuteCall)
        btnSpeaker = findViewById(R.id.btnSpeakerCall)
        btnVideo = findViewById(R.id.btnVideoCall)
        RuntimeUiStateManager.bind(applicationContext)
        runtimeLoadingOverlay = RuntimeLoadingOverlay.attach(this)
        runtimeSoftBanner = RuntimeSoftBannerManager.attach(this)
        callSearchingToneManager = CallSearchingToneManager()
        routeSearchingAnimator = RouteSearchingAnimator(lifecycleScope) { text -> setCallStatus(text) }
        lifecycleScope.launch(Dispatchers.IO) {
            RelayConfigValidator.validate(applicationContext, force = true)
        }
        observeRuntimeUiState()
        GhalbitCallManager.initialize(applicationContext)

        refreshPeerState()
        lifecycleScope.launch {
            peerGlobalId?.takeIf { it.isNotBlank() }?.let { globalId ->
                val candidate = PreparedRouteManager.requestSecondaryRoute(this@CallSessionActivity, callId, globalId, "LOCAL_MESH_DIRECT")
                val validated = PreparedRouteManager.validateSecondaryRoute(this@CallSessionActivity, candidate)
                preparedRouteStatusLabel = PreparedRouteManager.statusLabel(validated)
                setCallStatus(if (validated.ready) "Relay cadangan siap" else "MESH aktif")
            } ?: run {
                preparedRouteStatusLabel = PreparedRouteManager.statusLabel(null)
            }
        }
        if (guardSelfCall()) {
            finish()
            return
        }

        btnAccept.setOnClickListener {
            if (callActionInFlight) {
                Log.d("GHALBIT-CALL-ACTION", "duplicate click ignored")
                return@setOnClickListener
            }
            Log.d("GHALBIT-CALL-ACTION", "bypass runtime lock action=call:accept")
            if (!ActionDebounceManager.allow("call:accept:$callId", runtimeBusy = false, cooldownMs = 900L)) return@setOnClickListener
            acceptCall()
        }
        btnReject.setOnClickListener {
            if (callActionInFlight) {
                Log.d("GHALBIT-CALL-ACTION", "duplicate click ignored")
                return@setOnClickListener
            }
            Log.d("GHALBIT-CALL-ACTION", "bypass runtime lock action=call:reject")
            if (!ActionDebounceManager.allow("call:reject:$callId", runtimeBusy = false, cooldownMs = 900L)) return@setOnClickListener
            rejectCall()
        }
        btnEnd.setOnClickListener {
            Log.d("GHALBIT-CALL-ACTION", "bypass runtime lock action=call:end")
            if (!ActionDebounceManager.allow("call:end:$callId", runtimeBusy = false, cooldownMs = 900L)) return@setOnClickListener
            endCall()
        }
        btnMute.setOnClickListener {
            if (!ActionDebounceManager.allow("call:mute:$callId", runtimeBusy = false, cooldownMs = 400L)) return@setOnClickListener
            toggleMute()
        }
        btnSpeaker.setOnClickListener {
            if (!ActionDebounceManager.allow("call:speaker:$callId", runtimeBusy = false, cooldownMs = 400L)) return@setOnClickListener
            toggleSpeaker()
        }
        btnVideo.setOnClickListener {
            if (!ActionDebounceManager.allow("call:video:$callId", runtimeBusy = false, cooldownMs = 900L)) return@setOnClickListener
            toggleVideo()
        }
        btnTalk.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    beginTalk()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    finishTalk(sendAudio = true)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    finishTalk(sendAudio = false)
                    true
                }
                else -> false
            }
        }

        when (intent.getStringExtra(GhalbitDeepLinkRouter.EXTRA_CALL_ACTION)) {
            GhalbitDeepLinkRouter.ACTION_REJECT_CALL -> {
                CallRingtoneManager.stopIfCall(callId, "notification-reject-create")
                Log.d("GHALBIT-CALL-RING", "stop from notification action")
                Log.d("GHALBIT-CALL-RING", "stop on reject")
                rejectCall()
                return
            }
            GhalbitDeepLinkRouter.ACTION_ACCEPT_CALL -> {
                CallRingtoneManager.stopIfCall(callId, "notification-accept-create")
                Log.d("GHALBIT-CALL-RING", "stop from notification action")
                Log.d("GHALBIT-CALL-RING", "stop on accept")
                acceptCall()
                return
            }
            GhalbitDeepLinkRouter.ACTION_OPEN_CALL -> {
                CallRingtoneManager.stopIfCall(callId, "notification-open-create")
                Log.d("GHALBIT-DEEPLINK", "open call callId=$callId")
            }
        }

        speakerEnabled = true
        audioManager.isSpeakerphoneOn = true

        if (incoming) {
            updateState(CallState.INCOMING, getString(R.string.call_state_ringing))
            VoiceCallRegistry.start(
                callId = callId,
                peerName = peerName,
                peerIp = peerEndpoint?.routeHint ?: peerIp,
                peerGlobalId = peerGlobalId,
                localNodeId = MainActivity.myGlobalPeerId,
                routeHint = peerEndpoint?.routeHint ?: peerIp,
                state = CallState.INCOMING
            )
            Log.d("GHALBIT-CALL", "incoming received callId=$callId")
            CallRingtoneManager.startIncoming(this, callId)
            startStateTimeout(RINGING_TIMEOUT_MS)
        } else {
            updateState(CallState.OUTGOING, getString(R.string.call_state_outgoing))
            VoiceCallRegistry.start(
                callId = callId,
                peerName = peerName,
                peerIp = peerEndpoint?.routeHint ?: peerIp,
                peerGlobalId = peerGlobalId,
                localNodeId = MainActivity.myGlobalPeerId,
                routeHint = peerEndpoint?.routeHint ?: peerIp,
                state = CallState.OUTGOING
            )
            startStateTimeout(OUTGOING_TIMEOUT_MS)
            sendCallStart()
        }
        updateButtons()
    }

    override fun onResume() {
        super.onResume()
        runtimeLoadingOverlay.onHostResume()
        runtimeSoftBanner.onHostResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            packetReceiver,
            IntentFilter("com.ghalbitnet.meshx2.NEW_MESH_PACKET")
        )
        LocalBroadcastManager.getInstance(this).registerReceiver(
            audioReceiver,
            IntentFilter(FileTransferManager.ACTION_AUDIO_MESSAGE_RECEIVED)
        )
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(packetReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(audioReceiver)
        RuntimeUiStateManager.clearTransientState("call")
        ConversationKeepAliveManager.stopConversation(peerName)
        runtimeLoadingOverlay.onHostPause()
        runtimeSoftBanner.onHostPause()
        if (!isIncomingState(callState)) {
            CallRingtoneManager.stopIfCall(callId, "pause-non-incoming")
            Log.d("GHALBIT-CALL-RING", "cleanup lifecycle")
        }
        super.onPause()
    }

    override fun onDestroy() {
        ConversationKeepAliveManager.stopConversation(peerName)
        cleanupCall(sendEndIfNeeded = false)
        if (isFinishing || !isIncomingState(callState)) {
            CallRingtoneManager.stopIfCall(callId, "destroy")
            Log.d("GHALBIT-CALL-RING", "cleanup lifecycle")
        }
        runtimeLoadingOverlay.onHostDestroy()
        runtimeSoftBanner.onHostDestroy()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        when (intent.getStringExtra(GhalbitDeepLinkRouter.EXTRA_CALL_ACTION)) {
            GhalbitDeepLinkRouter.ACTION_ACCEPT_CALL -> {
                CallRingtoneManager.stopIfCall(callId, "notification-accept")
                Log.d("GHALBIT-CALL-RING", "stop from notification action")
                Log.d("GHALBIT-CALL-RING", "stop on accept")
                acceptCall()
            }
            GhalbitDeepLinkRouter.ACTION_REJECT_CALL -> {
                CallRingtoneManager.stopIfCall(callId, "notification-reject")
                Log.d("GHALBIT-CALL-RING", "stop from notification action")
                Log.d("GHALBIT-CALL-RING", "stop on reject")
                rejectCall()
            }
            GhalbitDeepLinkRouter.ACTION_OPEN_CALL -> {
                CallRingtoneManager.stopIfCall(callId, "notification-open")
                Log.d("GHALBIT-DEEPLINK", "open call callId=$callId")
            }
        }
    }

    private fun refreshPeerState() {
        val resolved =
            CentralIdentityResolver.resolve(
                context = this,
                legacyChatId = peerName,
                peerName = peerName,
                peerIp = peerIp,
                globalIdHint = peerGlobalId,
                publicKeyHint = peerPublicKey,
                walletAddressHint = peerWalletAddress,
                displayNameHint = peerDisplayName ?: peerName
            )
        callOwnershipHint =
            ConversationOwnershipHint(
                legacyChatId = peerName,
                globalId = resolved.globalId,
                publicKey = resolved.publicKey,
                walletAddress = resolved.walletAddress,
                canonicalDisplayName = resolved.displayName,
                lastKnownIp = resolved.peerIp.ifBlank { null },
                updatedAt = resolved.resolvedAt
            )
        peerGlobalId = resolved.globalId
        peerPublicKey = resolved.publicKey
        peerWalletAddress = resolved.walletAddress
        peerDisplayName = resolved.displayName ?: peerDisplayName
        peerIp = resolved.peerIp.ifBlank { peerIp }
        peerEndpoint =
            CallManager.resolvePeer(
                context = this,
                peerName = peerName,
                ipHint = peerIp,
                globalIdHint = peerGlobalId,
                publicKeyHint = peerPublicKey,
                walletAddressHint = peerWalletAddress,
                displayNameHint = peerDisplayName ?: peerName
            )
        peerEndpoint?.let {
            CallManager.rememberRoute(this, it, it.transportIp)
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val resolvedProfile =
                ProfileRepository.getResolvedContact(
                    context = this@CallSessionActivity,
                    globalId = peerGlobalId,
                    chatId = peerName,
                    fallbackDisplayName = peerDisplayName ?: peerName,
                    publicKeyHash = CallManager.publicKeyHash(peerPublicKey),
                    routeHint = peerEndpoint?.routeHint ?: peerIp
                )
            peerDisplayName = resolvedProfile.primaryName
            peerGlobalId?.takeIf { it.isNotBlank() }?.let {
                ProfileSyncManager.fetchProfile(this@CallSessionActivity, it)
            }
            withContext(Dispatchers.Main) {
                txtCallTitle.text =
                    getString(
                        R.string.call_with,
                        IdentityDisplayFormatter.primaryLabel(
                            canonicalDisplayName = peerDisplayName,
                            walletAddress = peerWalletAddress,
                            globalId = peerGlobalId,
                            publicKey = peerPublicKey,
                            legacyName = peerName,
                            ipAddress = peerEndpoint?.routeHint ?: peerIp
                        )
                    )
            }
        }
    }

    private fun guardSelfCall(): Boolean {
        val peer = peerEndpoint ?: return false
        val localGlobalId = GlobalMeshIdentityManager.buildGlobalId(keyStore.publicKeyBase64)
        val localPublicKeyHash = CallManager.localPublicKeyHash(this)
        val isSelf =
            CallManager.isSelfCall(
                localNodeId = MainActivity.myGlobalPeerId,
                localGlobalId = localGlobalId,
                localPublicKeyHash = localPublicKeyHash,
                peer = peer
            )
        if (isSelf) {
            Log.w("GHALBIT-CALL", "ignored self call")
            postUi {
                UiFeedbackManager.showToast(this, getString(R.string.call_self_ignored))
            }
        }
        return isSelf
    }

    private fun sendCallStart() {
        postUi { setCallStatus(getString(R.string.call_status_preparing)) }
        val peer = peerEndpoint
        if (peer?.routeHint.isNullOrBlank() && peer?.transportIp.isNullOrBlank()) {
            postUi { setCallStatus(getString(R.string.call_status_looking_for_peer)) }
            if (peer?.globalId.isNullOrBlank()) {
                postUi {
                    setCallStatus(getString(R.string.call_peer_missing))
                    UiFeedbackManager.showToast(this, getString(R.string.call_peer_missing))
                }
                updateState(CallState.CALL_FAILED, getString(R.string.call_state_failed))
                return
            }
            attemptRouteRecovery(
                statusMessage = "Mencari jalur ke kontak...",
                fallbackMessage = "Belum menemukan jalur. Pencarian tetap berjalan."
            )
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val routeType =
                if (!(peer?.routeHint ?: peer?.transportIp).isNullOrBlank()) {
                    TriplePathRoutePolicy.LOCAL_MESH_PRIMARY
                } else {
                    TriplePathRoutePolicy.STORE_FORWARD
                }
            if (peer != null && RouteProbeValidator.requiresProbe(routeType)) {
                val preflight = RouteProbeValidator.probe(this@CallSessionActivity, routeType, peer)
                if (!preflight.success) {
                    Log.d(
                        "GHALBIT-ROUTE-DISCOVERY",
                        "rejected staleHint=${preflight.staleHint} routeType=$routeType reason=${preflight.reason}"
                    )
                    postUi { setCallStatus("Mencoba jalur lain...") }
                    attemptRouteRecovery(
                        statusMessage = "Mencoba jalur lain...",
                        fallbackMessage = "Belum menemukan jalur. Pencarian tetap berjalan."
                    )
                    return@launch
                }
            }
            val localGlobalId = GlobalMeshIdentityManager.buildGlobalId(keyStore.publicKeyBase64)
            val localPublicKeyHash = CallManager.localPublicKeyHash(this@CallSessionActivity)
            val result =
                peer?.let {
                    GhalbitCallManager.startOutgoingCall(
                        context = this@CallSessionActivity,
                        callId = callId,
                        peer = it,
                        localNodeId = MainActivity.myGlobalPeerId,
                        localGlobalId = localGlobalId,
                        localPublicKeyHash = localPublicKeyHash
                    )
                }

            Log.d("GHALBIT-VOIP", "start callId=$callId engine=${result?.engineName ?: "NONE"} route=${result?.routeType}")
            if (result == null) {
                postUi {
                    updateState(CallState.CALL_FAILED, getString(R.string.call_state_failed))
                    Log.e("GHALBIT-CALL-RUNTIME", "sessionFailed callId=$callId reason=peer_missing")
                    UiFeedbackManager.showToast(this@CallSessionActivity, getString(R.string.call_peer_missing))
                }
                return@launch
            }
            realtimeFailed = result.fallbackToPtt
            postUi {
                if (result.fallbackToPtt) {
                    updateState(CallState.PTT_FALLBACK, result.statusMessage)
                } else {
                    updateState(CallState.SIGNALING_ACCEPT, result.statusMessage)
                }
            }
        }
    }

    private fun acceptCall() {
        val peer = peerEndpoint ?: return
        callActionInFlight = true
        waitingForRoute = false
        Log.d("GHALBIT-CALL-RUNTIME", "acceptPressed callId=$callId")
        Log.d("GHALBIT-CALL-ACTION", "accept clicked callId=$callId")
        Log.d("GHALBIT-CALL-ACTION", "ui immediate state=CONNECTING_CALL")
        val clickAt = System.currentTimeMillis()
        CallRingtoneManager.stopIfCall(callId, "accept_clicked")
        Log.d("GHALBIT-CALL-RUNTIME", "ringtoneStop callId=$callId")
        Log.d("GHALBIT-CALL-RING", "stop before network reason=accept_clicked")
        Log.d("GHALBIT-CALL-RING", "stop on accept")
        updateState(CallState.ACCEPT_CLICKED, "Menghubungkan...")
        updateButtons()
        val event = buildCallSignalEvent(CallManager.SIGNAL_CALL_ACCEPT, peer)
        if (event != null) {
            CallSignalQueue.enqueue(this, event)
        }
        lifecycleScope.launch(Dispatchers.IO) {
            Log.d("GHALBIT-CALL-PERF", "accept network launched background")
            Log.d("GHALBIT-CALL-PERF", "main thread safe")
            val relayValidation = RelayConfigValidator.validate(applicationContext, force = true)
            lastRelayValidation = relayValidation
            val routeScore = GhalbitCallManager.evaluateNearbyRouteScore(applicationContext, peer)
            Log.d("GHALBIT-VOICE-AUDIT", "relay config=${relayValidation.state}")
            Log.d("GHALBIT-VOICE-AUDIT", "mesh health=${routeScore.routeSummary}")
            val dispatchResult =
                event?.let { CallSignalQueue.dispatchNow(this@CallSessionActivity, it) }
                    ?: CallSignalDispatchResult(false, "ACCEPT_QUEUED", "Menunggu jalur tersedia.")
            postUi { Log.d("GHALBIT-CALL-PERF", "accept click to ui ms=${System.currentTimeMillis() - clickAt}") }
            if (routeScore.nearbyDetected || dispatchResult.delivered) {
                postUi {
                    callActionInFlight = false
                    waitingForRoute = false
                    updateState(CallState.CALL_CONNECTED_SIGNAL_ONLY, "Sinyal panggilan tersambung")
                }
                beginVoiceActivation("accept_clicked")
                return@launch
            }
            postUi {
                callActionInFlight = false
                waitingForRoute = true
                when {
                    relayValidation.state == RelayConfigValidation.State.INTERNET_RELAY_NOT_CONFIGURED -> updateState(CallState.RELAY_NOT_CONFIGURED, relayValidation.detail)
                    relayValidation.state == RelayConfigValidation.State.INTERNET_RELAY_UNREACHABLE -> updateState(CallState.RELAY_UNREACHABLE, relayValidation.detail)
                    routeScore.shouldDelayDemotion -> updateState(CallState.WAITING_FOR_AUDIO_PATH, "Perangkat dekat terdeteksi. Menguji suara lokal...")
                    else -> updateState(CallState.WAITING_FOR_ROUTE, "Menunggu jalur suara...")
                }
                updateButtons()
            }
            Log.d("GHALBIT-CALL-PERF", "accept timeout queued")
        }
    }

    private fun rejectCall() {
        val peer = peerEndpoint
        if (peer == null) {
            finishSafely()
            return
        }
        callActionInFlight = true
        Log.d("GHALBIT-CALL-ACTION", "reject clicked callId=$callId")
        Log.d("GHALBIT-CALL-ACTION", "ui immediate state=REJECTING_CALL")
        CallRingtoneManager.stopIfCall(callId, "reject_clicked")
        Log.d("GHALBIT-CALL-RING", "stop before network reason=reject_clicked")
        Log.d("GHALBIT-CALL-RING", "stop on reject")
        val event = buildCallSignalEvent(CallManager.SIGNAL_CALL_REJECT, peer)
        if (event != null) {
            CallSignalQueue.enqueue(this, event)
            lifecycleScope.launch(Dispatchers.IO) {
                CallSignalQueue.dispatchNow(this@CallSessionActivity, event)
            }
        }
        updateState(CallState.REJECTED, getString(R.string.call_rejected))
        saveCallNote(getString(R.string.call_note_rejected), isSent = false, status = "REJECTED")
        finishSafely()
    }

    private fun endCall() {
        if (finishedSafely) return
        updateState(CallState.CALL_ENDED, getString(R.string.call_state_ended))
        cleanupCall(sendEndIfNeeded = true)
        finishSafely()
    }

    private fun handleCallAccepted(payload: String) {
        if (CallManager.extractCallId(payload) != callId) return
        CallRingtoneManager.stopIfCall(callId, "connected-remote")
        Log.d("GHALBIT-CALL-RING", "stop on connected")
        updateState(CallState.CALL_CONNECTED_SIGNAL_ONLY, "Sinyal panggilan diterima")
        beginVoiceActivation("remote_accept")
    }

    private fun handleCallRejected(payload: String) {
        if (CallManager.extractCallId(payload) != callId) return
        CallRingtoneManager.stopIfCall(callId, "rejected-remote")
        Log.d("GHALBIT-CALL-RING", "stop on reject")
        updateState(CallState.REJECTED, getString(R.string.call_rejected))
        saveCallNote(getString(R.string.call_note_rejected), isSent = true, status = "REJECTED")
        finishSafely()
    }

    private fun handleCallEnded(payload: String) {
        if (CallManager.extractCallId(payload) != callId) return
        if (finishedSafely) return
        CallRingtoneManager.stopIfCall(callId, "ended-remote")
        Log.d("GHALBIT-CALL-RING", "stop on ended")
        updateState(CallState.CALL_ENDED, getString(R.string.call_state_ended))
        cleanupCall(sendEndIfNeeded = false)
        finishSafely()
    }

    private fun handleCallBusy(payload: String) {
        if (CallManager.extractCallId(payload) != callId) return
        CallRingtoneManager.stopIfCall(callId, "busy-remote")
        Log.d("GHALBIT-CALL-RING", "stop on ended")
        updateState(CallState.CALL_FAILED, getString(R.string.call_busy_remote))
        saveCallNote(getString(R.string.call_note_busy_remote), isSent = true, status = "BUSY")
        finishSafely()
    }

    private fun handleIncomingAudioFrame(payload: String) {
        if (CallManager.extractCallId(payload) != callId) return
        if (!isVoiceRealtimeState(callState)) return
        val packet = CallManager.parseVoicePacket(payload)
        if (packet != null) {
            adaptiveJitterBuffer.offer(packet)
            adaptiveJitterBuffer.drainReady()
            lastReceivedVoiceSequence = packet.sequence
            val ack = CallManager.buildVoiceAck(callId, packet.sequence, emptyList())
            Log.d("GHALBIT-VOICE-CHUNK", "ack seq=${ack.lastReceivedSequence}")
        }
        lastAudioPacketAt = System.currentTimeMillis()
        fullDuplexEngine.onIncomingAudioPacket(payload)
        Log.d("GHALBIT-CALL-AUDIO", "incoming frame callId=$callId")
    }

    private fun handleIncomingCallRefresh(payload: String) {
        if (CallManager.extractCallId(payload) != callId) return
        lastAudioPacketAt = System.currentTimeMillis()
        refreshPeerState()
    }

    private fun beginVoiceActivation(reason: String) {
        pendingVoiceHandshakeJob?.cancel()
        pendingVoiceHandshakeJob =
            lifecycleScope.launch(Dispatchers.IO) {
                stopStateTimeout()
                val peer = peerEndpoint ?: return@launch
                val relayValidation = lastRelayValidation ?: RelayConfigValidator.validate(applicationContext, force = false)
                val routeScore = GhalbitCallManager.evaluateNearbyRouteScore(applicationContext, peer)
                if (
                    ContextCompat.checkSelfPermission(this@CallSessionActivity, Manifest.permission.RECORD_AUDIO) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    Log.d("GHALBIT-PERMISSION", "mic requested")
                    postUi {
                        updateState(CallState.WAITING_FOR_AUDIO_PATH, "GhalbitNet memerlukan mikrofon untuk panggilan suara.")
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    return@launch
                }
                postUi {
                    updateState(CallState.WAITING_FOR_AUDIO_PATH, "Menguji suara lokal...")
                    if (routeScore.nearbyDetected) {
                        setCallStatus("Perangkat dekat terdeteksi")
                    }
                }
                if (routeScore.nearbyDetected) {
                    Log.d("GHALBIT-VOICE-FALLBACK", "delayed nearby=true")
                    val probeOk = voiceProbeManager.probeNearbyVoice()
                    if (!probeOk) {
                        Log.w("GHALBIT-VOICE-FALLBACK", "after probe failed")
                        postUi { showPttFallback("Jalur suara tidak stabil. Gunakan PTT.") }
                        return@launch
                    }
                    postUi { updateState(CallState.ROUTE_READY, "Suara lokal siap") }
                } else if (relayValidation.state != RelayConfigValidation.State.INTERNET_RELAY_READY) {
                    postUi { showPttFallback(relayValidation.detail.ifBlank { "Relay suara belum tersedia" }) }
                    Log.w("GHALBIT-VOICE-RELAY", "config missing")
                    return@launch
                } else {
                    Log.w("GHALBIT-VOICE-RELAY", "not implemented fallback ptt")
                    postUi { showPttFallback("Relay suara belum tersedia. Gunakan PTT.") }
                    return@launch
                }
                postUi { updateState(CallState.VOICE_HANDSHAKING, "Menguji jalur suara") }
                val handshakeOk = voiceProbeManager.handshakeVoiceTransport()
                if (!handshakeOk) {
                    Log.w("GHALBIT-VOICE-HANDSHAKE", "fallback ptt")
                    postUi { showPttFallback("Suara langsung belum tersedia. Gunakan pesan suara singkat / PTT.") }
                    return@launch
                }
                postUi { updateState(CallState.VOICE_TRANSPORT_READY, "Transport suara siap") }
                voicePreProcessor.prepare()
                val engineReady = voiceAudioEngine.start(speakerEnabled)
                if (!engineReady || !voiceAudioEngine.isCaptureActive() || !voiceAudioEngine.isPlaybackActive()) {
                    postUi { showPttFallback("Audio device belum siap. Gunakan PTT.") }
                    return@launch
                }
                postUi {
                    realtimeFailed = false
                    updateState(CallState.VOICE_STREAM_ACTIVE, "Suara aktif")
                    applyVoiceMode(AdaptiveVoiceMode.LIVE_VOICE, "voice transport ready")
                    setCallStatus("Suara lokal siap")
                }
                startAudioWatchdog()
                lifecycleScope.launch {
                    delay(8_000L)
                    if (!finishedSafely && !isVoiceRealtimeState(callState)) {
                        Log.w("GHALBIT-CALL-PERF", "audio wait timeout")
                        showPttFallback("Menunggu jalur suara...")
                    }
                }
                Log.d("GHALBIT-CALL-PERF", "accept background launched")
                Log.d("GHALBIT-VOICE-AUDIT", "connected set source=$reason")
            }
    }

    private fun startStateTimeout(timeoutMs: Long) {
        stopStateTimeout()
        timeoutJob =
            lifecycleScope.launch {
                delay(timeoutMs)
                if (finishedSafely || isVoiceRealtimeState(callState) || isTerminalCallState(callState)) {
                    return@launch
                }
                CallRingtoneManager.stopIfCall(callId, "incoming-timeout")
                Log.d("GHALBIT-CALL-RING", "incoming timeout")
                val message =
                    if (incoming) getString(R.string.call_missed) else getString(R.string.call_no_answer)
                updateState(if (incoming) CallState.MISSED else CallState.CALL_FAILED, message)
                if (incoming) {
                    AppNotificationManager.notifyMissedCall(
                        context = applicationContext,
                        peerName = peerName,
                        peerGlobalId = peerGlobalId,
                        peerPublicKey = peerPublicKey,
                        peerWalletAddress = peerWalletAddress,
                        peerDisplayName = peerDisplayName
                    )
                    Log.d("GHALBIT-CALL", "missed timeout")
                }
                cleanupCall(sendEndIfNeeded = true)
                finishSafely()
            }
    }

    private fun stopStateTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    private fun startAudioWatchdog() {
        audioWatchdogJob?.cancel()
        lastAudioPacketAt = System.currentTimeMillis()
        audioWatchdogJob =
            lifecycleScope.launch {
                while (isVoiceRealtimeState(callState) && !finishedSafely) {
                    delay(1000)
                    val gapMs = System.currentTimeMillis() - lastAudioPacketAt
                    val snapshot =
                        VoiceQualityMonitor.evaluate(
                            packetLoss = when {
                                gapMs >= 8_000L -> 100
                                gapMs >= 5_000L -> 50
                                gapMs >= 2_500L -> 22
                                else -> 4
                            },
                            jitterMs = (gapMs / 20L).toInt().coerceAtMost(180),
                            audioGapMs = gapMs,
                            queueDelayMs = voicePlaybackScheduler.playbackDelayFor(currentVoiceMode)
                        )
                    lastBandwidthSnapshot =
                        bandwidthEstimator.estimate(
                            rttMs = snapshot.jitterMs.toLong() * 2L,
                            packetLossPercent = snapshot.packetLoss,
                            ackDelayMs = snapshot.audioGapMs.coerceAtMost(2_000L),
                            routeStability = (100 - snapshot.packetLoss).coerceAtLeast(5)
                        )
                    Log.d("GHALBIT-VOICE-QUALITY", "bandwidthKbps=${lastBandwidthSnapshot?.estimatedKbps}")
                    val nextMode = VoiceModeDecisionEngine.decide(currentVoiceMode, snapshot)
                    if (nextMode != currentVoiceMode) {
                        postUi {
                            applyVoiceMode(nextMode, "audio_watchdog")
                            when (nextMode) {
                                AdaptiveVoiceMode.BUFFERED_VOICE -> setCallStatus("Suara tertunda")
                                AdaptiveVoiceMode.VOICE_CAPACITOR -> setCallStatus("Mode kapasitor suara")
                                AdaptiveVoiceMode.AI_RECONSTRUCTED_SPEECH -> setCallStatus("AI menyambungkan suara karena jaringan buruk")
                                AdaptiveVoiceMode.PTT_STORE_FORWARD -> showPttFallback(getString(R.string.call_realtime_unstable))
                                AdaptiveVoiceMode.LIVE_VOICE -> setCallStatus("Suara aktif")
                            }
                        }
                    }
                    if (gapMs > NO_AUDIO_TIMEOUT_MS) {
                        attemptRouteRecovery(
                            statusMessage = "Jalur putus, mencari ulang...",
                            fallbackMessage = "Belum menemukan jalur. Pencarian tetap berjalan."
                        )
                        break
                    }
                }
            }
    }

    private fun stopAudioWatchdog() {
        audioWatchdogJob?.cancel()
        audioWatchdogJob = null
    }

    private fun sendSignal(type: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val peer = peerEndpoint ?: return@launch
            val event = buildCallSignalEvent(type, peer)
            if (event != null) {
                CallSignalQueue.enqueue(this@CallSessionActivity, event)
                CallSignalQueue.dispatchNow(this@CallSessionActivity, event)
            }
        }
    }

    private fun buildCallSignalEvent(type: String, peer: CallPeerEndpoint): CallSignalEvent? {
        val localNodeId = MainActivity.myGlobalPeerId.takeIf { it.isNotBlank() } ?: return null
        return CallSignalEvent(
            eventId = "$type-$callId-${System.currentTimeMillis()}",
            callId = callId,
            type = type,
            peerName = peerName,
            nodeId = peer.nodeId,
            globalId = peer.globalId,
            publicKey = peer.publicKey,
            publicKeyHash = peer.publicKeyHash,
            walletAddress = peer.walletAddress,
            displayName = peer.displayName,
            routeHint = peer.routeHint,
            transportIp = peer.transportIp,
            localNodeId = localNodeId,
            localGlobalId = GlobalMeshIdentityManager.buildGlobalId(keyStore.publicKeyBase64),
            localPublicKeyHash = CallManager.localPublicKeyHash(this)
        )
    }

    private fun beginTalk() {
        if (!isPttFallbackState(callState) && !isVoiceRealtimeState(callState)) return
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingVoiceStart = true
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        startTalkRecording()
    }

    private fun finishTalk(sendAudio: Boolean) {
        pendingVoiceStart = false
        if (!isRecording) return
        stopTalkRecording(cancelOnly = !sendAudio)
    }

    private fun startTalkRecording() {
        if (isRecording) return
        try {
            Log.d("GHALBIT-PTT-FALLBACK", "record start")
            val voiceDir = File(cacheDir, "call_voice")
            if (!voiceDir.exists()) {
                voiceDir.mkdirs()
            }
            val outputFile = File(voiceDir, "call_${System.currentTimeMillis()}.m4a")
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
            postUi { btnTalk.text = getString(R.string.call_release_to_send) }
            startRecordingTicker()
        } catch (e: Exception) {
            MeshLogger.e("CALL", "Record start failed", e)
            postUi { setCallStatus(getString(R.string.chat_voice_failed)) }
            stopTalkRecording(cancelOnly = true)
        }
    }

    private fun stopTalkRecording(cancelOnly: Boolean) {
        val recorder = mediaRecorder
        val audioFile = currentRecordingFile
        mediaRecorder = null
        currentRecordingFile = null
        val durationMs = System.currentTimeMillis() - recordStartAt
        recordStartAt = 0L
        isRecording = false
        recordingStatusJob?.cancel()
        recordingStatusJob = null
        postUi { btnTalk.text = getString(R.string.call_hold_to_talk) }

        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.reset() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        Log.d("GHALBIT-PTT-FALLBACK", "record stop")

        if (cancelOnly || audioFile == null || !audioFile.exists()) {
            audioFile?.delete()
            return
        }
        if (durationMs < MIN_VOICE_DURATION_MS) {
            audioFile.delete()
            postUi { setCallStatus(getString(R.string.chat_voice_too_short)) }
            return
        }
        sendFallbackVoice(audioFile)
    }

    private fun sendFallbackVoice(audioFile: File) {
        Log.d("GHALBIT-PTT-FALLBACK", "queued")
        applyVoiceMode(AdaptiveVoiceMode.PTT_STORE_FORWARD, "fallback send")
        runCatching {
            val bytes = audioFile.readBytes()
            val speechFrame = VoiceFrame(bytes = bytes.copyOfRange(0, minOf(bytes.size, 640)), energy = 0.8)
            when (vadEngine.classify(speechFrame)) {
                VadDecision.SPEECH,
                VadDecision.UNCERTAIN -> {
                    speechPriorityQueue.offer(
                        PrioritizedVoiceFrame(
                            sequenceNumber = 1,
                            frame = speechFrame,
                            priority = VoiceFramePriority.HIGH
                        )
                    )
                }
                VadDecision.NOISE,
                VadDecision.SILENCE -> Unit
            }
            val profile = codecAdapter.select(currentVoiceMode)
            val chunk = voiceCapacitorBuffer.captureChunk(bytes, durationMs = 1000, codec = profile.codecName, isLastInBurst = true)
            Log.d("GHALBIT-VOICE-CAPACITOR", "queued seq=${chunk.sequenceNumber}")
            Log.d("GHALBIT-VOICE-CHUNK", "sent seq=${chunk.sequenceNumber}")
        }
        postUi { setCallStatus(getString(R.string.call_sending_voice)) }
        FileTransferManager.sendFile(
            context = this,
            fileUri = Uri.fromFile(audioFile),
            destinationPeerId = peerName,
            keyStore = keyStore,
            myPeerId = MainActivity.myGlobalPeerId,
            listener =
                object : FileTransferManager.TransferStatusListener {
                    override fun onProgress(message: String, busy: Boolean) {
                        postUi { setCallStatus(getString(R.string.call_sending_voice)) }
                    }

                    override fun onComplete(message: String) {
                        Log.d("GHALBIT-PTT-FALLBACK", "sent")
                        postUi { setCallStatus("PTT terkirim") }
                    }

                    override fun onError(message: String) {
                        postUi {
                            setCallStatus(getString(R.string.chat_voice_failed))
                            UiFeedbackManager.showToast(this@CallSessionActivity, getString(R.string.chat_voice_failed))
                        }
                    }
                }
        )
    }

    private fun playIncomingAudio(filePath: String, label: String) {
        try {
            applyVoiceMode(AdaptiveVoiceMode.BUFFERED_VOICE, "incoming fallback audio")
            releasePlayer()
            runCatching {
                val file = File(filePath)
                if (file.exists()) {
                    val bytes = file.readBytes()
                    val chunk =
                        VoiceChunk(
                            chunkId = "incoming-${System.currentTimeMillis()}",
                            callId = callId,
                            senderGlobalId = peerGlobalId ?: peerName,
                            sequenceNumber = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
                            capturedAt = System.currentTimeMillis(),
                            durationMs = 1000,
                            codec = "PCM_FALLBACK",
                            compressedBytes = bytes,
                            checksum = "local",
                            isLastInBurst = true
                        )
                    voiceChunkAssembler.add(chunk)
                    voiceChunkAssembler.nextBurst()
                }
            }
            mediaPlayer =
                MediaPlayer().apply {
                    setDataSource(filePath)
                    setOnCompletionListener {
                        releasePlayer()
                        setCallStatus("PTT diterima")
                    }
                    prepare()
                    start()
                }
            setCallStatus("${getString(R.string.call_audio_incoming)} $label")
        } catch (e: Exception) {
            MeshLogger.e("CALL", "Play incoming audio failed", e)
        }
    }

    private fun releasePlayer() {
        try { mediaPlayer?.stop() } catch (_: Exception) {}
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun startRecordingTicker() {
        recordingStatusJob?.cancel()
        recordingStatusJob =
            lifecycleScope.launch {
                while (isRecording) {
                    val elapsed = System.currentTimeMillis() - recordStartAt
                    setCallStatus("${getString(R.string.chat_recording)} ${formatDuration(elapsed)}")
                    delay(250)
                }
            }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun toggleMute() {
        muted = !muted
        GhalbitCallManager.muteMic(muted)
        fullDuplexEngine.setMuted(muted)
        postUi { btnMute.text = getString(if (muted) R.string.call_unmute else R.string.call_mute) }
        Log.d("GHALBIT-CALL-UI", "mute=$muted")
    }

    private fun toggleSpeaker() {
        speakerEnabled = !speakerEnabled
        GhalbitCallManager.setSpeaker(speakerEnabled)
        audioManager.isSpeakerphoneOn = speakerEnabled
        postUi {
            btnSpeaker.text =
                getString(if (speakerEnabled) R.string.call_speaker_off else R.string.call_speaker_on)
        }
        Log.d("GHALBIT-CALL-UI", "speaker=$speakerEnabled")
    }

    private fun toggleVideo() {
        val peer = peerEndpoint ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            if (videoActive) {
                GhalbitCallManager.stopVideo()
                videoActive = false
                postUi {
                    btnVideo.text = getString(R.string.call_video_start)
                    setCallStatus(getString(R.string.call_audio_only))
                }
                return@launch
            }
            val result =
                GhalbitCallManager.startVideoCall(
                    context = this@CallSessionActivity,
                    callId = callId,
                    peer = peer,
                    localNodeId = MainActivity.myGlobalPeerId,
                    localGlobalId = GlobalMeshIdentityManager.buildGlobalId(keyStore.publicKeyBase64),
                    localPublicKeyHash = CallManager.localPublicKeyHash(this@CallSessionActivity)
                )
            videoActive = result.videoAllowed
            postUi {
                btnVideo.text = getString(if (videoActive) R.string.call_video_stop else R.string.call_video_start)
                setCallStatus(result.statusMessage)
                if (!videoActive) {
                    UiFeedbackManager.showToast(this@CallSessionActivity, result.statusMessage)
                }
            }
        }
    }

    private fun updateState(state: CallState, message: String) {
        val previous = callState
        callState = state
        if (isSettledCallState(state)) {
            callActionInFlight = false
            waitingForRoute = state == CallState.WAITING_FOR_ROUTE
        }
        if (!isIncomingState(state)) {
            CallRingtoneManager.stopIfCall(callId, "state-$state")
        }
        if (isVoiceRealtimeState(state)) {
            Log.d("GHALBIT-CALL-RING", "stop on connected")
            Log.d("GHALBIT-CALL-STATE", "voice active")
        } else if (isTerminalCallState(state)) {
            Log.d("GHALBIT-CALL-RING", "stop on ended")
        }
        RuntimeUiStateManager.onCallState(state, message)
        if (
            state == CallState.CALL_CONNECTED_SIGNAL_ONLY ||
            state == CallState.WAITING_FOR_AUDIO_PATH ||
            state == CallState.VOICE_HANDSHAKING ||
            state == CallState.VOICE_TRANSPORT_READY ||
            isVoiceRealtimeState(state)
        ) {
            ConversationKeepAliveManager.startConversation(
                context = this,
                chatId = peerName,
                globalId = peerGlobalId,
                routeHint = peerIp,
                preferFastPing = true
            )
        } else if (isTerminalCallState(state) || state == CallState.IDLE) {
            ConversationKeepAliveManager.stopConversation(peerName)
        }
        val current = callSession
        callSession =
            if (current == null) {
                CallSession(
                    callId = callId,
                    localNodeId = MainActivity.myGlobalPeerId,
                    remoteNodeId = peerName,
                    remoteGlobalId = peerGlobalId,
                    state = state,
                    routeHint = peerEndpoint?.routeHint ?: peerIp,
                    lastPacketAt = System.currentTimeMillis()
                )
            } else {
                current.copy(
                    state = state,
                    lastPacketAt = System.currentTimeMillis(),
                    routeHint = peerEndpoint?.routeHint ?: peerIp
                )
            }
        callSession?.let { VoiceCallRegistry.updateSession(it) }
        Log.d("GHALBIT-CALL-STATE", "old=$previous new=$state reason=$message")
        if (state == CallState.ROUTE_READY || state == CallState.VOICE_STREAM_ACTIVE || state == CallState.CONNECTED) {
            Log.d("GHALBIT-CALL-RUNTIME", "sessionReady callId=$callId state=$state")
        }
        if (state == CallState.CALL_FAILED) {
            Log.e("GHALBIT-CALL-RUNTIME", "sessionFailed callId=$callId reason=$message")
        }
        if (state == CallState.CALL_CONNECTED_SIGNAL_ONLY) {
            Log.d("GHALBIT-CALL-STATE", "signal-only")
        }
        if (state == CallState.WAITING_FOR_AUDIO_PATH) {
            Log.d("GHALBIT-CALL-STATE", "waiting audio path")
        }
        if (state == CallState.PTT_FALLBACK) {
            Log.d("GHALBIT-CALL-STATE", "ptt fallback")
        }
        setCallStatus(message)
        updateButtons()
    }

    private fun updateButtons() {
        postUi {
            btnAccept.isEnabled = incoming && isIncomingState(callState) && !callActionInFlight && !waitingForRoute
            btnReject.isEnabled = incoming && isIncomingState(callState) && !callActionInFlight && !waitingForRoute
            btnEnd.isEnabled = callState != CallState.IDLE || waitingForRoute
            btnAccept.text = if (callActionInFlight || waitingForRoute) "MENUNGGU..." else getString(R.string.call_accept)
            btnReject.text = if (callActionInFlight && !waitingForRoute) "MEMPROSES..." else getString(R.string.call_reject)
            val connected = isVoiceRealtimeState(callState)
            val pttFallback = isPttFallbackState(callState)
            btnMute.isEnabled = connected
            btnSpeaker.isEnabled = connected
            btnTalk.isEnabled = connected || pttFallback
            btnVideo.isEnabled = connected
            btnVideo.visibility =
                if ((peerEndpoint?.let { GhalbitCallManager.videoCapability(this, it).videoRecommended } ?: false) && !realtimeFailed) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            btnTalk.visibility = if (pttFallback || waitingForRoute) View.VISIBLE else View.GONE
        }
    }

    private fun setCallStatus(baseStatus: String) {
        postUi {
            val hint =
                IdentityDisplayFormatter.secondaryLabel(
                    primaryLabel = IdentityDisplayFormatter.primaryLabel(
                        canonicalDisplayName = peerDisplayName,
                        walletAddress = peerWalletAddress,
                        globalId = peerGlobalId,
                        publicKey = peerPublicKey,
                        legacyName = peerName,
                        ipAddress = peerEndpoint?.routeHint ?: peerIp
                    ),
                    legacyName = peerName,
                    walletAddress = peerWalletAddress,
                    globalId = peerGlobalId,
                    publicKey = peerPublicKey,
                    ipAddress = peerEndpoint?.routeHint ?: peerIp
                )
            val routeLine = preparedRouteStatusLabel.takeIf { it.isNotBlank() }
            val voiceModeLine = humanVoiceModeLabel()
            val technicalLine = technicalVoiceDetailLine()
            val body =
                buildString {
                    append(baseStatus.trim())
                    voiceModeLine?.let {
                        append("\n")
                        append(it)
                    }
                    hint?.takeIf { it.isNotBlank() }?.let {
                        append("\n")
                        append(it)
                    }
                    routeLine?.let {
                        append("\n")
                        append(it)
                    }
                    technicalLine?.let {
                        append("\n")
                        append(it)
                    }
                }
            txtCallStatus.text = body
            Log.d("GHALBIT-UX", "call state=$callState status=${baseStatus.trim()}")
        }
    }

    private fun applyVoiceMode(mode: AdaptiveVoiceMode, reason: String) {
        val previous = currentVoiceMode
        currentVoiceMode = mode
        lastVoiceModeSwitchAt = System.currentTimeMillis()
        val profile = codecAdapter.select(mode)
        Log.d("GHALBIT-AUDIO-ADAPT", "bitrate=${profile.bitrateKbps}")
        Log.d("GHALBIT-AUDIO-ADAPT", "mode=$mode")
        Log.d("GHALBIT-AUDIO-JITTER", "bufferMs=${voicePlaybackScheduler.playbackDelayFor(mode)}")
        if (profile.fecEnabled) Log.d("GHALBIT-AUDIO-FEC", "enabled")
        if (profile.dtxEnabled) Log.d("GHALBIT-AUDIO-DTX", "enabled")
        when (mode) {
            AdaptiveVoiceMode.LIVE_VOICE -> {
                Log.d("GHALBIT-VOICE-MODE", "live")
                if (previous != mode) {
                    Log.d("GHALBIT-VOICE-MODE", "upgraded")
                    Log.d("GHALBIT-VOICE-MODE", "restore live voice")
                    Log.d("GHALBIT-AI-BRIDGE", "deactivated")
                    VoiceOutputMixer.restoreLive()
                }
                Log.d("GHALBIT-VOICE-STREAM", "active verified")
            }
            AdaptiveVoiceMode.BUFFERED_VOICE -> {
                Log.d("GHALBIT-VOICE-MODE", "buffered")
                if (previous != mode) Log.d("GHALBIT-VOICE-MODE", "downgraded")
            }
            AdaptiveVoiceMode.VOICE_CAPACITOR -> {
                Log.d("GHALBIT-VOICE-MODE", "capacitor")
                if (previous != mode) Log.d("GHALBIT-VOICE-MODE", "downgraded")
            }
            AdaptiveVoiceMode.PTT_STORE_FORWARD -> {
                Log.d("GHALBIT-VOICE-MODE", "store_forward")
                if (previous != mode) Log.d("GHALBIT-VOICE-MODE", "downgraded")
            }
            AdaptiveVoiceMode.AI_RECONSTRUCTED_SPEECH -> {
                if (!CommunicationSettingsManager.isLocalAiTranscriptEnabled(this)) {
                    showPttFallback("Mode AI lokal dimatikan. Gunakan pesan suara singkat.")
                    return
                }
                Log.w("GHALBIT-AI-VOICE", "critical mode triggered")
                Log.w("GHALBIT-AI-VOICE", "switched from audio to text")
                Log.w("GHALBIT-AI-VOICE", "reason=low_bandwidth")
                Log.d("GHALBIT-AI-BRIDGE", "activated callId=$callId")
                Log.d("GHALBIT-AI-VOICE", "ui shown")
                Log.d("GHALBIT-AI-VOICE", "consent ok")
                Log.d("GHALBIT-AI-VOICE", "privacy local preferred")
                val started =
                    speechToTextEngine.start(callId, GhalbitCallManager.localGlobalId().ifBlank { "unknown" }) { packet ->
                    aiVoiceTranscriptAssembler.add(packet)
                    Log.d("GHALBIT-AI-VOICE", "packet created bytes=${packet.text.toByteArray().size}")
                    Log.d("GHALBIT-AI-VOICE", "packet queued")
                    if (packet.priority == "CRITICAL") {
                        Log.w("GHALBIT-AI-VOICE", "emergency phrase detected")
                        Log.d("GHALBIT-AI-VOICE", "priority packet sent")
                    } else {
                        Log.d("GHALBIT-AI-VOICE", "packet sent")
                    }
                }
                if (!started) {
                    showPttFallback("Pengenal suara lokal belum tersedia. Gunakan PTT.")
                    return
                }
                VoiceOutputMixer.fadeOriginalOut()
                VoiceOutputMixer.fadeTtsIn()
                if (reason.isNotBlank()) {
                    setCallStatus("AI menyambungkan suara karena jaringan buruk")
                }
            }
        }
    }

    private fun humanVoiceModeLabel(): String? {
        return when (currentVoiceMode) {
            AdaptiveVoiceMode.LIVE_VOICE -> "Suara langsung"
            AdaptiveVoiceMode.BUFFERED_VOICE -> "Suara ditahan sebentar agar stabil"
            AdaptiveVoiceMode.VOICE_CAPACITOR -> "Suara dicicil melalui jaringan lemah"
            AdaptiveVoiceMode.PTT_STORE_FORWARD -> "Tekan bicara: pesan suara dikirim bertahap"
            AdaptiveVoiceMode.AI_RECONSTRUCTED_SPEECH -> "Mode hemat jaringan: suara dibantu teks lokal"
        }
    }

    private fun technicalVoiceDetailLine(): String? {
        if (!DeveloperModeManager.isEnabled(this) || !CommunicationSettingsManager.isTechnicalDetailEnabled(this)) {
            return null
        }
        val snapshot = lastBandwidthSnapshot ?: return null
        return "Teknis: ~${snapshot.estimatedKbps} kbps • RTT ${snapshot.rttMs} ms • Loss ${snapshot.packetLossPercent}% • Seq $lastReceivedVoiceSequence"
    }

    private fun observeRuntimeUiState() {
        lifecycleScope.launch {
            RuntimeUiStateManager.stateFlow.collectLatest { snapshot ->
                runtimeLoadingOverlay.render(snapshot)
                runtimeSoftBanner.render(snapshot)
            }
        }
    }

    private fun cleanupCall(sendEndIfNeeded: Boolean) {
        if (sendEndIfNeeded && !endSignalSent) {
            endSignalSent = true
            val peer = peerEndpoint
            if (peer != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    GhalbitCallManager.endCall(
                        context = this@CallSessionActivity,
                        callId = callId,
                        peer = peer,
                        localNodeId = MainActivity.myGlobalPeerId,
                        localGlobalId = GlobalMeshIdentityManager.buildGlobalId(keyStore.publicKeyBase64),
                        localPublicKeyHash = CallManager.localPublicKeyHash(this@CallSessionActivity)
                    )
                }
            } else {
                sendSignal(CallManager.SIGNAL_CALL_END)
            }
        }
        stopStateTimeout()
        stopAudioWatchdog()
        routeRecoveryJob?.cancel()
        routeSearchingAnimator.stop()
        callSearchingToneManager.stopAndRelease()
        pendingVoiceHandshakeJob?.cancel()
        CallRingtoneManager.stopIfCall(callId, "cleanup")
        voiceAudioEngine.stop()
        speechToTextEngine.stop()
        speechToTextEngine.release()
        aiVoicePlaybackEngine.shutdown()
        GhalbitCallManager.stopVideo()
        stopTalkRecording(cancelOnly = true)
        releasePlayer()
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        } catch (_: Exception) {
        }
        VoiceCallRegistry.clear()
    }

    private fun showPttFallback(message: String) {
        realtimeFailed = true
        routeSearchingAnimator.stop()
        callSearchingToneManager.stop()
        updateState(CallState.PTT_FALLBACK, message)
        applyVoiceMode(AdaptiveVoiceMode.PTT_STORE_FORWARD, "ptt fallback")
        setCallStatus(message)
        Log.d("GHALBIT-VOICE-AUDIT", "fallback=PTT")
        Log.d("GHALBIT-PTT-FALLBACK", "ui shown")
    }

    private fun attemptRouteRecovery(
        statusMessage: String,
        fallbackMessage: String
    ) {
        if (routeRecoveryJob?.isActive == true || finishedSafely) return
        routeRecoveryJob =
            lifecycleScope.launch {
                updateState(CallState.WAITING_FOR_ROUTE, statusMessage)
                routeSearchingAnimator.start(statusMessage)
                callSearchingToneManager.start()
                runtimeSoftBanner.showMessage(
                    key = "call:route:search:$callId",
                    title = "Mencari ulang jalur",
                    detail = statusMessage,
                    priority = 4,
                    durationMs = 2400L,
                    miniStatus = "Mencari..."
                )
                val currentPeer = peerEndpoint
                val discovery =
                    CallRouteDiscoveryManager.discoverForCall(
                        context = this@CallSessionActivity,
                        peerName = peerName,
                        ipHint = currentPeer?.routeHint ?: currentPeer?.transportIp ?: peerIp,
                        globalIdHint = currentPeer?.globalId ?: peerGlobalId,
                        publicKeyHint = currentPeer?.publicKey ?: peerPublicKey,
                        walletAddressHint = currentPeer?.walletAddress ?: peerWalletAddress,
                        displayNameHint = currentPeer?.displayName ?: peerDisplayName
                    ) { _, label ->
                        routeSearchingAnimator.update(label)
                    }
                routeSearchingAnimator.stop(discovery.humanStatus)
                callSearchingToneManager.stop()
                val endpoint = discovery.endpoint
                if (endpoint == null) {
                    runtimeSoftBanner.showMessage(
                        key = "call:route:failed:$callId",
                        title = "Belum menemukan jalur",
                        detail = fallbackMessage,
                        priority = 5,
                        durationMs = 2600L,
                        miniStatus = "Mencari..."
                    )
                    showPttFallback(fallbackMessage)
                    return@launch
                }
                peerEndpoint =
                    currentPeer?.copy(
                        routeHint = endpoint.routeHint ?: endpoint.transportIp,
                        transportIp = endpoint.transportIp ?: endpoint.routeHint,
                        globalId = endpoint.globalId ?: currentPeer.globalId,
                        publicKey = endpoint.publicKey ?: currentPeer.publicKey,
                        publicKeyHash = endpoint.publicKeyHash ?: currentPeer.publicKeyHash,
                        walletAddress = endpoint.walletAddress ?: currentPeer.walletAddress,
                        displayName = endpoint.displayName ?: currentPeer.displayName
                    ) ?: endpoint
                preparedRouteStatusLabel = when (discovery.selectedRouteType) {
                    TriplePathRoutePolicy.SERVER_DIRECT_INTERNET -> "Server induk siap"
                    TriplePathRoutePolicy.INTERNET_RELAY -> "Relay cadangan siap"
                    TriplePathRoutePolicy.LOCAL_MESH_PRIMARY -> "Mesh lokal aktif"
                    TriplePathRoutePolicy.LOCAL_MESH_SECONDARY -> "Mesh cadangan aktif"
                    TriplePathRoutePolicy.IDENTITY_COPY_TRACE -> "Jalur copy identitas"
                    else -> preparedRouteStatusLabel
                }
                runtimeSoftBanner.showMessage(
                    key = "call:route:ok:$callId",
                    title = "Jalur ditemukan",
                    detail = preparedRouteStatusLabel,
                    priority = 3,
                    durationMs = 1600L,
                    miniStatus = preparedRouteStatusLabel
                )
                beginVoiceActivation("route_recovery")
            }
    }

    private fun completeSignalAck(type: String) {
        signalAckWaiters[type]?.complete(true)
    }

    private fun replyVoiceAck(type: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val peer = peerEndpoint ?: return@launch
            CallManager.sendCustomSignal(
                context = this@CallSessionActivity,
                peer = peer,
                type = type,
                payload = CallManager.buildSignalPayload(
                    callId = callId,
                    localNodeId = MainActivity.myGlobalPeerId,
                    localGlobalId = GlobalMeshIdentityManager.buildGlobalId(keyStore.publicKeyBase64),
                    localPublicKeyHash = CallManager.localPublicKeyHash(this@CallSessionActivity)
                ),
                localNodeId = MainActivity.myGlobalPeerId
            )
        }
    }

    private fun isIncomingState(state: CallState): Boolean =
        state == CallState.RINGING || state == CallState.INCOMING

    private fun isVoiceRealtimeState(state: CallState): Boolean =
        state == CallState.VOICE_STREAM_ACTIVE || state == CallState.CONNECTED

    private fun isPttFallbackState(state: CallState): Boolean =
        state == CallState.PTT_FALLBACK || realtimeFailed

    private fun isTerminalCallState(state: CallState): Boolean =
        state == CallState.ENDED ||
            state == CallState.CALL_ENDED ||
            state == CallState.FAILED ||
            state == CallState.CALL_FAILED ||
            state == CallState.REJECTED ||
            state == CallState.MISSED

    private fun isSettledCallState(state: CallState): Boolean =
        state == CallState.CALL_CONNECTED_SIGNAL_ONLY ||
            state == CallState.WAITING_FOR_AUDIO_PATH ||
            state == CallState.VOICE_HANDSHAKING ||
            state == CallState.VOICE_TRANSPORT_READY ||
            state == CallState.VOICE_STREAM_ACTIVE ||
            state == CallState.PTT_FALLBACK ||
            state == CallState.WAITING_FOR_ROUTE ||
            state == CallState.RELAY_NOT_CONFIGURED ||
            state == CallState.RELAY_UNREACHABLE ||
            state == CallState.MESH_STALE ||
            isTerminalCallState(state) ||
            state == CallState.IDLE

    private fun finishSafely() {
        if (finishedSafely) return
        finishedSafely = true
        cleanupCall(sendEndIfNeeded = false)
        postUi { finish() }
    }

    private fun saveCallNote(content: String, isSent: Boolean, status: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                ChatDatabase.getInstance(applicationContext).chatDao().insertMessage(
                    ChatMessage(
                        packetId = "CALL-NOTE-$callId-$status-${System.currentTimeMillis()}",
                        chatId = peerName,
                        senderName = if (isSent) "ME" else peerName,
                        content = content,
                        contentType = "CALL",
                        isSent = isSent,
                        status = status
                    )
                )
            } catch (e: Exception) {
                MeshLogger.e("CALL", "Save call note failed", e)
            }
        }
    }

    private fun postUi(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }
}
