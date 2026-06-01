package com.ghalbitnet.meshx2

import android.annotation.SuppressLint

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ghalbitnet.meshx2.blockchain.BlockchainLedger
import com.ghalbitnet.meshx2.call.CallSessionActivity
import com.ghalbitnet.meshx2.call.VoiceCallRegistry
import com.ghalbitnet.meshx2.chat.ChatDatabase
import com.ghalbitnet.meshx2.chat.ChatDeliveryManager
import com.ghalbitnet.meshx2.chat.InternalEventRouter
import com.ghalbitnet.meshx2.chat.ChatMessage
import com.ghalbitnet.meshx2.chat.AdaptiveRouteManager
import com.ghalbitnet.meshx2.chat.RouteEvidenceSource
import com.ghalbitnet.meshx2.chat.ContactListActivity
import com.ghalbitnet.meshx2.chat.RemoteModeActivity
import com.ghalbitnet.meshx2.chat.SavedContactsActivity
import com.ghalbitnet.meshx2.chat.MessagingReceiver
import com.ghalbitnet.meshx2.chat.ConversationKeepAliveManager
import com.ghalbitnet.meshx2.core.network.ConnectivityStatusDetector
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.core.network.HybridConnectivityPlanner
import com.ghalbitnet.meshx2.core.network.InternetGatewayRegistry
import com.ghalbitnet.meshx2.dashboard.RuntimeDashboardActivity
import com.ghalbitnet.meshx2.routing.PacketTtlManager
import com.ghalbitnet.meshx2.core.network.LatencyEngine
import com.ghalbitnet.meshx2.core.network.TransportPreference
import com.ghalbitnet.meshx2.discovery.DiscoveryManager
import com.ghalbitnet.meshx2.discovery.PeerDiscoveryHandler
import com.ghalbitnet.meshx2.discovery.UdpDiscovery
import com.ghalbitnet.meshx2.diagnostics.NetworkTruthProbe
import com.ghalbitnet.meshx2.economy.MeshEconomyActivity
import com.ghalbitnet.meshx2.economy.ServicePathRecorder
import com.ghalbitnet.meshx2.economy.UsageSessionRecorder
import com.ghalbitnet.meshx2.file.FileTransferManager
import com.ghalbitnet.meshx2.identity.CentralIdentityResolver
import com.ghalbitnet.meshx2.identity.IdentityDiagnosticsFormatter
import com.ghalbitnet.meshx2.identity.IdentityBridge
import com.ghalbitnet.meshx2.identity.IdentityDisplayFormatter
import com.ghalbitnet.meshx2.identity.IdentityRegistry
import com.ghalbitnet.meshx2.model.MeshNode
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.monitor.NetworkActivity
import com.ghalbitnet.meshx2.debug.DebugActivity
import com.ghalbitnet.meshx2.stats.MeshStatistics
import com.ghalbitnet.meshx2.core.node.NodeStatusManager
import com.ghalbitnet.meshx2.nearby.NearbyManager
import com.ghalbitnet.meshx2.network.MeshSocketClient
import com.ghalbitnet.meshx2.network.MeshSocketServer
import com.ghalbitnet.meshx2.network.AckTracker
import com.ghalbitnet.meshx2.network.ReliablePacketSender
import com.ghalbitnet.meshx2.routing.MeshRegistry
import com.ghalbitnet.meshx2.routing.RouteDiscovery
import com.ghalbitnet.meshx2.routing.RouteMaintenanceManager
import com.ghalbitnet.meshx2.security.CryptoEngine
import com.ghalbitnet.meshx2.security.KeyStoreManager
import com.ghalbitnet.meshx2.service.MeshForegroundService
import com.ghalbitnet.meshx2.settings.ChatMediaSettingsActivity
import com.ghalbitnet.meshx2.settings.NotificationSettingsActivity
import com.ghalbitnet.meshx2.online.OnlineFallbackTransport
import com.ghalbitnet.meshx2.util.LogThrottle
import com.ghalbitnet.meshx2.profile.MyProfileActivity
import com.ghalbitnet.meshx2.online.OnlinePresenceManager
import com.ghalbitnet.meshx2.token.TokenManager
import com.ghalbitnet.meshx2.wifi.WifiDirectManager
import com.ghalbitnet.meshx2.wireguard.WireGuardMeshManager
import com.ghalbitnet.meshx2.core.utils.SafeNavigator
import com.ghalbitnet.meshx2.core.runtime.LightweightMeshSupervisor
import com.ghalbitnet.meshx2.core.runtime.MeshHeartbeatTicker
import com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager
import com.ghalbitnet.meshx2.core.runtime.MeshRuntimeState
import com.ghalbitnet.meshx2.core.runtime.NetworkHandoffMonitor
import com.ghalbitnet.meshx2.core.recovery.MeshAutoRecovery
import com.ghalbitnet.meshx2.core.health.MeshHealthReporter
import com.ghalbitnet.meshx2.core.utils.AppNotificationManager
import com.ghalbitnet.meshx2.sos.SosAlertManager
import com.ghalbitnet.meshx2.sos.SosInboxActivity
import com.ghalbitnet.meshx2.ui.ActionDebounceManager
import com.ghalbitnet.meshx2.ui.GhalbitTheme
import com.ghalbitnet.meshx2.ui.RuntimeLoadingOverlay
import com.ghalbitnet.meshx2.ui.RuntimeSoftBannerManager
import com.ghalbitnet.meshx2.ui.RuntimeUiState
import com.ghalbitnet.meshx2.ui.RuntimeUiStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    companion object {
        var myGlobalPeerId: String = ""
        private const val MAX_LOG_LINES = 160
        private const val UI_REFRESH_DEBOUNCE_MS = 350L
    }

    private lateinit var txtStatus: TextView
    private lateinit var txtNodes: TextView
    private lateinit var txtPing: TextView
    private lateinit var txtBalance: TextView
    private lateinit var txtConnectionScope: TextView
    private lateinit var txtGlobalIdentity: TextView
    private lateinit var txtHybridStatus: TextView
    private lateinit var mainScroll: ScrollView
    private lateinit var txtLog: TextView
    private lateinit var logScroll: NestedScrollView
    private lateinit var txtUserMessage: TextView
    private lateinit var progressMain: ProgressBar
    private lateinit var runtimeLoadingOverlay: RuntimeLoadingOverlay
    private lateinit var runtimeSoftBanner: RuntimeSoftBannerManager

    private lateinit var keyStore: KeyStoreManager
    private lateinit var chatDb: ChatDatabase
    private lateinit var ledger: BlockchainLedger
    private lateinit var peerDiscoveryHandler: PeerDiscoveryHandler

    private var wifiDirect: WifiDirectManager? = null
    private var nearby: NearbyManager? = null
    private var wgManager: WireGuardMeshManager? = null
    private var discoveryHeartbeatJob: Job? = null
    private var pingUpdateJob: Job? = null
    private var uiRefreshJob: Job? = null
    private var routeMaintenanceJob: Job? = null
    private var meshStarted = false
    private var buttonsInitialized = false
    private var lastUserMessage: String = ""
    private var lastBusyState: Boolean = false
    private var runtimeObserversStarted = false
    private var lastLogAutoScrollAt: Long = 0L
    private var isMainScrollActive: Boolean = false
    private var lastMainScrollAt: Long = 0L
    private var lastAppliedRuntimeKey: String = ""
    private var lastAppliedStatusLabel: String = ""
    private var lastProgressBusy: Boolean = false
    private val scrollStateHandler = Handler(Looper.getMainLooper())
    private val clearScrollStateRunnable = Runnable { isMainScrollActive = false }

    private var myIp: String = "10.0.0.3"
    private lateinit var myPeerId: String

    private val fileTransferStatusReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                val message =
                    intent?.getStringExtra(FileTransferManager.EXTRA_MESSAGE)
                        ?: return

                val busy =
                    intent.getBooleanExtra(
                        FileTransferManager.EXTRA_BUSY,
                        false
                    )

                setUserMessage(message, busy)

                if (!busy) {
                    appendLog(message)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        GhalbitTheme.applyWindow(this, "MainActivity")

        initializeViews()
        initializeCore()
        RuntimeUiStateManager.bind(applicationContext)
        runtimeLoadingOverlay = RuntimeLoadingOverlay.attach(this)
        runtimeSoftBanner = RuntimeSoftBannerManager.attach(this)
        initializeButtons()
        requestPermissionsIfNeeded()
        observeRuntimeUiState()
        MeshRuntimeManager.logRuntimeVerification("activityCreated")

        if (allPermissionsGranted()) {
            startMesh()
        }
    }

    private fun initializeViews() {
        txtStatus = findViewById(R.id.txtStatus)
        txtNodes = findViewById(R.id.txtNodes)
        txtPing = findViewById(R.id.txtPing)
        txtBalance = findViewById(R.id.txtBalance)
        txtConnectionScope = findViewById(R.id.txtConnectionScope)
        txtGlobalIdentity = findViewById(R.id.txtGlobalIdentity)
        txtHybridStatus = findViewById(R.id.txtHybridStatus)
        mainScroll = findViewById(R.id.mainScroll)
        txtLog = findViewById(R.id.txtLog)
        logScroll = findViewById(R.id.logScroll)
        txtUserMessage = findViewById(R.id.txtUserMessage)
        progressMain = findViewById(R.id.progressMain)
        mainScroll.overScrollMode = View.OVER_SCROLL_NEVER
        mainScroll.setOnScrollChangeListener { _, _, _, _, _ ->
            if (!isMainScrollActive) {
                Log.d("GHALBIT-SCROLL-AUDIT", "start")
            }
            isMainScrollActive = true
            lastMainScrollAt = System.currentTimeMillis()
            scrollStateHandler.removeCallbacks(clearScrollStateRunnable)
            scrollStateHandler.postDelayed(clearScrollStateRunnable, 420L)
        }
        logScroll.isNestedScrollingEnabled = false
        logScroll.overScrollMode = View.OVER_SCROLL_NEVER
        GhalbitTheme.logCardRendered("main-dashboard")
    }

    private fun setUserMessage(
        message: String,
        busy: Boolean = false
    ) {
        if (message == lastUserMessage && busy == lastBusyState) {
            return
        }

        lastUserMessage = message
        lastBusyState = busy

        runOnUiThread {
            if (txtUserMessage.text.toString() != message) {
                txtUserMessage.text = message
            } else {
                Log.d("GHALBIT-SCROLL-AUDIT", "skipped same state")
            }
            if (lastProgressBusy != busy) {
                progressMain.visibility = if (busy) View.VISIBLE else View.INVISIBLE
                lastProgressBusy = busy
                Log.d("GHALBIT-BLAST-GUARD", "reduced relayout source=userMessage")
            }
        }
    }

    private fun appendLog(
        message: String
    ) {
        runOnUiThread {
            appendLogRaw("\n$message")
        }
    }

    private fun appendLogRaw(text: String) {
        txtLog.append(text)
        trimLog()
        val now = System.currentTimeMillis()
        if (shouldAutoScrollLog(now)) {
            logScroll.post {
                val target = txtLog.bottom + txtLog.paddingBottom
                logScroll.scrollTo(0, target)
            }
            lastLogAutoScrollAt = now
        }
    }

    private fun shouldAutoScrollLog(now: Long): Boolean {
        if (now - lastLogAutoScrollAt < 700L) {
            Log.d("GHALBIT-UX-PERF", "log autoscroll throttled")
            return false
        }
        val child = logScroll.getChildAt(0) ?: return false
        val distanceToBottom = child.bottom - (logScroll.height + logScroll.scrollY)
        return distanceToBottom <= 96
    }

    private fun trimLog() {
        val lines =
            txtLog.text
                .toString()
                .lineSequence()
                .filter { it.isNotEmpty() }
                .toList()

        if (lines.size <= MAX_LOG_LINES) {
            return
        }

        txtLog.text =
            lines.takeLast(MAX_LOG_LINES).joinToString("\n")
    }

    private fun initializeCore() {
        keyStore = KeyStoreManager(this)
        chatDb = ChatDatabase.getInstance(this)
        ChatDeliveryManager.bind(applicationContext)
        TokenManager.init(this)
        peerDiscoveryHandler = PeerDiscoveryHandler(this, keyStore)

        myIp = getLocalIpAddress() ?: "10.0.0.3"

        RouteDiscovery.init(this, myIp)

        ledger = BlockchainLedger.getInstance(applicationContext)

        generatePeerId()
        txtGlobalIdentity.text =
            GlobalMeshIdentityManager.buildGlobalId(keyStore.publicKeyBase64)
        startRouteCleaner()
    }

    private fun generatePeerId() {
        val hash =
            MessageDigest.getInstance("SHA-256")
                .digest(keyStore.publicKeyBase64.toByteArray())

        val hex = StringBuilder()

        for (b in hash) {
            hex.append(String.format("%02x", b))
        }

        myPeerId = hex.toString().take(8)
        myGlobalPeerId = myPeerId
        MeshRuntimeManager.configureLocalPeer(myPeerId)
        ServicePathRecorder.initialize(applicationContext, myPeerId)
        UsageSessionRecorder.initialize(applicationContext)

        Log.d("GHALBIT", "Peer ID: $myPeerId")
    }

    private fun startRouteCleaner() {
        routeMaintenanceJob?.cancel()
        routeMaintenanceJob =
            RouteMaintenanceManager.start(
                scope = lifecycleScope,
                listener =
                    object : RouteMaintenanceManager.RouteMaintenanceListener {
                        override fun onMaintenanceStatus(message: String) {
                            Log.d("GHALBIT", message)
                        }

                        override fun onMaintenanceError(
                            message: String,
                            throwable: Throwable?
                        ) {
                            Log.e("GHALBIT", message, throwable)
                        }
                    }
            )
    }

    private fun stopRouteCleaner() {
        routeMaintenanceJob?.cancel()
        routeMaintenanceJob = null
    }

    private fun startMesh() {
        if (meshStarted) {
            MeshRuntimeManager.logRuntimeVerification("meshAlreadyStarted")
            requestRefreshUi(0L)
            return
        }

        meshStarted = true
        Log.d("GHALBIT", "Starting mesh")
        txtStatus.text = "STARTING"
        setUserMessage(getString(R.string.starting_mesh), true)
        RuntimeUiStateManager.setTransientState(
            source = "main:startMesh",
            state = RuntimeUiState.PREPARING,
            title = "Menyiapkan jaringan",
            detail = "Runtime sedang menghidupkan listener dan heartbeat."
        )
        appendLog("Menyiapkan layanan mesh...")

        startForegroundMeshService()

        wgManager = WireGuardMeshManager(this)

        lifecycleScope.launch {
            wgManager?.startMesh("10.0.0.3/24")
        }

        UdpDiscovery.init(keyStore)
        UdpDiscovery.setLocalIdentity(
            peerId = myPeerId,
            publicKey = keyStore.publicKeyBase64
        )

        MeshSocketServer.appContext = applicationContext
        MeshSocketServer.localPeerId = myPeerId

        startSocketServer()
        NetworkHandoffMonitor.updateTcpListenerRunning(MeshSocketServer.isRunning())
        NetworkHandoffMonitor.start(
            context = applicationContext,
            listener = object : NetworkHandoffMonitor.Listener {
                override fun onSubnetChanged(oldIp: String, newIp: String, oldSubnet: String, newSubnet: String) {
                    NetworkHandoffMonitor.markRediscovering(true)
                    UdpDiscovery.stop()
                    startDiscoveryListener()
                    startDiscoveryHeartbeat()
                    broadcastLocalNode()
                    MeshSocketServer.ensureRunning("networkChanged")
                    NetworkHandoffMonitor.updateTcpListenerRunning(MeshSocketServer.isRunning())
                    Log.w("GHALBIT-NETWORK-HANDOFF", "staleRoutesCleared oldSubnet=$oldSubnet newSubnet=$newSubnet")
                    Log.w("GHALBIT-NETWORK-HANDOFF", "directHintsCleared oldIp=$oldIp newIp=$newIp")
                    Log.w("GHALBIT-NETWORK-HANDOFF", "discoveryRestarted")
                    NetworkHandoffMonitor.markRediscovering(false)
                }
            }
        )
        startDiscoveryListener()
        startDiscoveryHeartbeat()
        broadcastLocalNode()
        MeshRuntimeManager.start()
        initializeMeshFeatures()
        lifecycleScope.launch(Dispatchers.IO) {
            OnlinePresenceManager.bind(applicationContext)
            if (com.ghalbitnet.meshx2.BuildConfig.INTERNET_RELAY_CONFIGURED) {
                LogThrottle.d(
                    "GHALBIT-ANDROID-RELAY",
                    "relay-config:main",
                    "config relayUrl=${com.ghalbitnet.meshx2.BuildConfig.BASE_RELAY_URL}",
                    10_000L,
                    applicationContext
                )
            } else {
                LogThrottle.w(
                    "GHALBIT-ANDROID-RELAY",
                    "relay-missing:main",
                    "missing config",
                    10_000L,
                    applicationContext
                )
            }
            OnlinePresenceManager.registerOnline(
                context = applicationContext,
                nodeId = myPeerId,
                globalId = GlobalMeshIdentityManager.buildGlobalId(keyStore.publicKeyBase64),
                publicKeyHash = com.ghalbitnet.meshx2.call.CallManager.publicKeyHash(keyStore.publicKeyBase64)
            )
        }
        txtStatus.text = "ONLINE"
        setUserMessage(getString(R.string.mesh_searching), false)
        RuntimeUiStateManager.setTransientState(
            source = "main:startMesh",
            state = RuntimeUiState.DISCOVERING,
            title = "Mencari node",
            detail = "Mencari jalur terbaik..."
        )
        appendLog("Mesh aktif. Menunggu node terdekat...")
        MeshRuntimeManager.logRuntimeVerification("appStarted")
        requestRefreshUi(0L)
        observeRuntimeState()

        startAutoRecovery()
    }

    private fun startAutoRecovery() {
        MeshAutoRecovery.start {
            try {
                MeshRuntimeManager.start()
            } catch (_: Exception) {
            }

            try {
                MeshRuntimeState.heartbeat()
            } catch (_: Exception) {
            }
            try {
                MeshSocketServer.ensureRunning("healthCheck")
                NetworkHandoffMonitor.updateTcpListenerRunning(MeshSocketServer.isRunning())
            } catch (_: Exception) {
            }
        }
    }

    private fun startSocketServer() {
        MeshRuntimeManager.markSocketServerActive(true)
        MeshSocketServer.start(
            onPacket = { packet ->
                MeshRuntimeManager.onPacketProcessed()
                MeshRuntimeManager.recordPacketSummary("${packet.type} from ${packet.source}")
                processIncomingPacket(packet)
            },
            onSecure = { secure ->
                try {
                    MessagingReceiver.onSecurePacket(
                        secure,
                        keyStore,
                        chatDb
                    )
                } catch (e: Exception) {
                    Log.e("GHALBIT", "Secure packet error", e)
                }
            }
        )
    }

    private fun startForegroundMeshService() {
        try {
            val intent =
                Intent(
                    this,
                    MeshForegroundService::class.java
                )

            ContextCompat.startForegroundService(
                this,
                intent
            )
        } catch (e: Exception) {
            Log.e("GHALBIT", "Foreground service start failed", e)
        }
    }

    private fun startDiscoveryHeartbeat() {
        if (discoveryHeartbeatJob?.isActive == true) {
            return
        }

        discoveryHeartbeatJob =
            lifecycleScope.launch {
                while (true) {
                    try {
                        broadcastLocalNode()
                    } catch (e: Exception) {
                        Log.e("GHALBIT", "Discovery heartbeat failed", e)
                    }

                    delay(10000)
                }
            }
    }

    private fun processIncomingPacket(packet: MeshPacket) {
        try {
            val payload =
                if (packet.encrypted) {
                    decryptPayload(packet)
                } else {
                    packet.payload
                }

            runOnUiThread {
                MeshStatistics.receivedPacket(packet.type, packet.source)

                if (packet.type == "ACK") {
                    AckTracker.markAckReceived(packet.payload)
                }

                appendLogRaw("\nPACKET ${packet.type}")
                appendLogRaw("\nFROM ${packet.source}")
                appendLogRaw("\nMSG $payload")
            }

            val intent =
                Intent("com.ghalbitnet.meshx2.NEW_MESH_PACKET")

            intent.putExtra("packetId", packet.packetId)
            intent.putExtra("source", packet.source)
            intent.putExtra("destination", packet.destination)
            intent.putExtra("payload", payload)
            intent.putExtra("type", packet.type)
            intent.putExtra("encrypted", packet.encrypted)

            LocalBroadcastManager
                .getInstance(this)
                .sendBroadcast(intent)

            if (packet.type == "CHAT") {
                handleIncomingChatMessage(packet, payload)
                sendAck(packet)
            }

            if (packet.type == "ROUTE_CHECK") {
                val ackFor = runCatching { JSONObject(payload).optString("packetId", packet.packetId) }.getOrDefault(packet.packetId)
                sendRouteAck(packet.source, ackFor)
            }

            if (packet.type == "ROUTE_ACK") {
                val ackFor = runCatching { JSONObject(payload).optString("ackFor") }.getOrDefault("")
                if (ackFor.isNotBlank()) {
                    AckTracker.markAckReceived(ackFor)
                }
            }

            if (packet.type == "VOICE_PROBE") {
                val parsed = runCatching { JSONObject(payload) }.getOrNull()
                val callId = parsed?.optString("callId").orEmpty()
                Log.d("GHALBIT-VOICE-PROBE", "received source=${packet.source} callId=$callId")
                val ackFor = parsed?.optString("packetId")?.ifBlank { packet.packetId } ?: packet.packetId
                val audioPath = if (VoiceCallRegistry.activeSession != null) "PROBE_READY" else "PROBE_RECEIVED_NO_ACTIVE_CALL"
                sendVoiceAck(packet.source, ackFor = ackFor, callId = callId, audioPath = audioPath)
            }

            if (packet.type == "VOICE_ACK") {
                val ackFor = runCatching { JSONObject(payload).optString("ackFor") }.getOrDefault("")
                if (ackFor.isNotBlank()) {
                    AckTracker.markAckReceived(ackFor)
                }
                Log.d("GHALBIT-VOICE-PROBE", "ack source=${packet.source}")
            }

            if (packet.type == "ACK" || packet.type == "CHAT_ACK" || packet.type == "CHAT_DELIVERED") {
                ChatDeliveryManager.handleAck(applicationContext, packet.payload)
            }

            if (packet.type == "CHAT_READ") {
                ChatDeliveryManager.handleRead(applicationContext, packet.payload)
            }

            if (packet.type == "PING" || packet.type == "PONG" || packet.type == "ROUTE_CHECK") {
                ConversationKeepAliveManager.onPacketReceived(applicationContext, packet, payload)
            }

            val probeReply = NetworkTruthProbe.onIncomingPacket(myPeerId, packet, payload)
            if (probeReply != null) {
                val peerIp = keyStore.getPeerAddress(packet.source)
                if (!peerIp.isNullOrBlank()) {
                    MeshSocketClient.send(peerIp, probeReply)
                }
            }

            if (packet.type == "SOS") {
                handleIncomingSos(packet, payload)
            }

            if (packet.type == "CALL_INVITE" || packet.type == "CALL_START") {
                handleIncomingCallInvite(packet, payload)
            }

        } catch (e: Exception) {
            Log.e("GHALBIT", "Packet error", e)
        }
    }

    private fun handleIncomingCallInvite(
        packet: MeshPacket,
        payload: String
    ) {
        val callId =
            try {
                JSONObject(payload).optString("callId")
            } catch (_: Exception) {
                ""
            }

        if (callId.isBlank()) {
            return
        }

        val peerIp =
            keyStore.getPeerAddress(packet.source).orEmpty()
        com.ghalbitnet.meshx2.call.CallManager.rememberRoute(
            applicationContext,
            com.ghalbitnet.meshx2.call.CallPeerEndpoint(
                nodeId = packet.source,
                globalId = com.ghalbitnet.meshx2.call.CallManager.extractSourceGlobalId(payload),
                publicKeyHash = com.ghalbitnet.meshx2.call.CallManager.extractSourcePublicKeyHash(payload),
                routeHint = peerIp,
                transportIp = peerIp
            ),
            peerIp
        )
        val resolvedIdentity =
            CentralIdentityResolver.resolve(
                context = applicationContext,
                legacyChatId = packet.source,
                peerName = packet.source,
                peerIp = peerIp,
                publicKeyHint = keyStore.getPeerKey(packet.source),
                displayNameHint = packet.source
            )
        val identityRecord = resolvedIdentity.toIdentityRecord()

        if (VoiceCallRegistry.isBusy()) {
            sendCallSignalToPeer(
                targetPeerId = packet.source,
                targetIp = peerIp,
                type = "CALL_BUSY",
                callId = callId
            )
            appendLog("Panggilan dari ${packet.source} ditolak otomatis karena sesi lain masih aktif.")
            saveCallNote(
                peerName = packet.source,
                content = getString(R.string.call_note_busy_local),
                isSent = false,
                status = "BUSY"
            )
            return
        }

        val intent =
            CallSessionActivity.createIntent(
                context = this,
                peerName = packet.source,
                peerIp = peerIp,
                callId = callId,
                incoming = true,
                peerGlobalId = resolvedIdentity.globalId,
                peerPublicKey = resolvedIdentity.publicKey,
                peerWalletAddress = resolvedIdentity.walletAddress,
                peerDisplayName = resolvedIdentity.displayName
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        AppNotificationManager.notifyIncomingCall(
            context = applicationContext,
            peerName = packet.source,
            peerIp = peerIp,
            callId = callId,
            peerGlobalId = resolvedIdentity.globalId,
            peerPublicKey = resolvedIdentity.publicKey,
            peerWalletAddress = resolvedIdentity.walletAddress,
            peerDisplayName = resolvedIdentity.displayName
        )
        startActivity(intent)
    }

    private fun handleIncomingChatMessage(
        packet: MeshPacket,
        payload: String
    ) {
        // TODO unified identity:
        // incoming chat should resolve sender by globalId instead of storing
        // packet.source directly as chatId/senderName.
        val cleanPayload =
            PacketTtlManager.extractMessage(payload)
                .ifBlank { payload }
        val peerPublicKey =
            keyStore.getPeerKey(packet.source)
        val peerIp =
            keyStore.getPeerAddress(packet.source).orEmpty()
        val resolvedIdentity =
            CentralIdentityResolver.resolve(
                context = applicationContext,
                legacyChatId = packet.source,
                peerName = packet.source,
                peerIp = peerIp,
                publicKeyHint = peerPublicKey,
                displayNameHint = packet.source
            )
        val identityRecord = resolvedIdentity.toIdentityRecord()

        Log.d(
            "GHALBIT",
            "CHAT_IDENTITY ${IdentityDiagnosticsFormatter.formatResolved(resolvedIdentity)}"
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                ChatDeliveryManager.handleIncomingMessage(
                    context = applicationContext,
                    packet = packet,
                    payload = cleanPayload,
                    peerIp = peerIp
                ) {
                    if (chatDb.chatDao().countByPacketId(packet.packetId) == 0) {
                        val internalEvent =
                            InternalEventRouter.toChatMessage(
                                context = applicationContext,
                                packetId = packet.packetId,
                                chatId = packet.source,
                                senderName = packet.source,
                                type = packet.type,
                                payload = cleanPayload,
                                isSent = false,
                                status = "DELIVERED",
                                senderGlobalId = resolvedIdentity.globalId,
                                publicDisplayName = resolvedIdentity.displayName ?: packet.source
                            )
                        chatDb.chatDao().insertMessage(
                            internalEvent ?: ChatMessage(
                                packetId = packet.packetId,
                                chatId = packet.source,
                                senderName = packet.source,
                                content = cleanPayload,
                                contentType = "TEXT",
                                isSent = false,
                                status = "DELIVERED"
                            )
                        )
                    }
                }

                if (!com.ghalbitnet.meshx2.chat.ChatActivity.isViewingChatWith(packet.source)) {
                    val identity =
                        com.ghalbitnet.meshx2.identity.IdentityRegistry.findByLegacy(
                            peerName = packet.source,
                            ipAddress = packet.destination
                        )
                    val internalEvent =
                        InternalEventRouter.toChatMessage(
                            context = applicationContext,
                            packetId = packet.packetId,
                            chatId = packet.source,
                            senderName = packet.source,
                            type = packet.type,
                            payload = cleanPayload,
                            isSent = false,
                            status = "DELIVERED",
                            senderGlobalId = identity?.globalId,
                            publicDisplayName = identity?.displayName ?: packet.source
                        )

                    if (internalEvent?.visibilityType != com.ghalbitnet.meshx2.chat.MessageVisibility.HIDDEN.name) {
                        AppNotificationManager.notifyChatMessage(
                            context = applicationContext,
                            peerName = packet.source,
                            message = (internalEvent?.content ?: cleanPayload).take(120),
                            peerGlobalId = identity?.globalId,
                            peerPublicKey = identity?.publicKey,
                            peerWalletAddress = identity?.walletAddress,
                            peerDisplayName = identity?.displayName
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("GHALBIT", "Incoming chat save failed", e)
            }
        }
    }

    private fun sendCallSignalToPeer(
        targetPeerId: String,
        targetIp: String,
        type: String,
        callId: String
    ) {
        // TODO unified identity:
        // outbound call signaling should resolve destination by globalId and
        // use peerId/IP only as transport hints.
        if (targetIp.isBlank()) {
            return
        }

        CentralIdentityResolver.resolve(
            context = applicationContext,
            legacyChatId = targetPeerId,
            peerName = targetPeerId,
            peerIp = targetIp,
            publicKeyHint = keyStore.getPeerKey(targetPeerId),
            displayNameHint = targetPeerId
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val payload =
                    JSONObject()
                        .put("callId", callId)
                        .put("peerName", myPeerId)
                        .toString()

                ReliablePacketSender.sendWithRetry(
                    targetIp,
                    MeshPacket(
                        packetId = "$type-${System.currentTimeMillis()}",
                        source = myPeerId,
                        destination = targetPeerId,
                        type = type,
                        payload = payload,
                        encrypted = false
                    )
                )
            } catch (e: Exception) {
                Log.e("GHALBIT", "Call signal send failed", e)
            }
        }
    }

    private fun saveCallNote(
        peerName: String,
        content: String,
        isSent: Boolean,
        status: String
    ) {
        CentralIdentityResolver.resolve(
            context = applicationContext,
            legacyChatId = peerName,
            peerName = peerName,
            peerIp = keyStore.getPeerAddress(peerName).orEmpty(),
            publicKeyHint = keyStore.getPeerKey(peerName),
            displayNameHint = peerName
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                chatDb.chatDao().insertMessage(
                    ChatMessage(
                        packetId = "CALL-NOTE-${peerName}-${status}-${System.currentTimeMillis()}",
                        chatId = peerName,
                        senderName = if (isSent) "ME" else peerName,
                        content = content,
                        contentType = "CALL_EVENT",
                        messageType = com.ghalbitnet.meshx2.chat.MessageVisibility.SYSTEM_EVENT.name,
                        visibilityType = com.ghalbitnet.meshx2.chat.MessageVisibility.VISIBLE.name,
                        internalSignalType = status,
                        isSent = isSent,
                        status = status
                    )
                )
            } catch (e: Exception) {
                Log.e("GHALBIT", "Call note save failed", e)
            }
        }
    }

    private fun handleIncomingSos(
        packet: MeshPacket,
        payload: String
    ) {
        val routeHint =
            keyStore.getPeerAddress(packet.source).orEmpty()
        val alert =
            SosAlertManager.handleIncomingSos(
                context = applicationContext,
                packet = packet,
                payload = payload,
                routeHint = routeHint
            ) ?: return
        AdaptiveRouteManager.recordRouteEvidence(
            chatId = packet.source,
            globalId = alert.sourceGlobalId,
            nextHop = routeHint.takeIf { it.isNotBlank() },
            transport = "LOCAL_MESH_DIRECT",
            source = RouteEvidenceSource.SOS,
            confidence = 92
        )
        val resolvedIdentity =
            CentralIdentityResolver.resolve(
                context = applicationContext,
                legacyChatId = packet.source,
                peerName = packet.source,
                peerIp = routeHint,
                publicKeyHint = keyStore.getPeerKey(packet.source),
                displayNameHint = packet.source
            )
        val identityRecord = resolvedIdentity.toIdentityRecord()

        lifecycleScope.launch(Dispatchers.IO) {
            if (chatDb.chatDao().countByPacketId(packet.packetId) == 0) {
                chatDb.chatDao().insertMessage(
                    ChatMessage(
                        packetId = packet.packetId,
                        chatId = packet.source,
                        senderName = packet.source,
                        content = "SOS ALERT: $payload",
                        contentType = "SOS",
                        isSent = false,
                        status = "RECEIVED"
                    )
                )
            }
        }

        runOnUiThread {
            val sosLabel =
                IdentityDisplayFormatter.primaryLabel(
                    canonicalDisplayName = resolvedIdentity.displayName,
                    walletAddress = resolvedIdentity.walletAddress,
                    globalId = resolvedIdentity.globalId,
                    publicKey = resolvedIdentity.publicKey,
                    legacyName = packet.source
                )
            txtUserMessage.text =
                getString(
                    R.string.sos_banner_message,
                    sosLabel,
                    alert.sourceGlobalId ?: "-",
                    alert.message
                )
            Toast.makeText(this, "SOS from $sosLabel: ${alert.message}", Toast.LENGTH_LONG).show()
            appendLogRaw("\nSOS ALERT FROM $sosLabel: ${alert.message} | route=${alert.routeHint ?: "-"}")
            Log.d("GHALBIT-SOS-UI", "render alertId=${alert.alertId} source=${alert.sourceNodeId}")
        }
    }

    private fun decryptPayload(packet: MeshPacket): String {
        return try {
            val peerPubKey =
                keyStore.getPeerKey(packet.source)
                    ?: return "[NO KEY]"

            val sharedSecret =
                CryptoEngine.deriveSharedSecret(
                    keyStore.privateKey,
                    CryptoEngine.base64ToPublicKey(peerPubKey)
                )

            val encryptedBytes =
                Base64.decode(packet.payload, Base64.DEFAULT)

            String(
                CryptoEngine.decrypt(
                    encryptedBytes,
                    sharedSecret
                )
            )
        } catch (e: Exception) {
            "[DECRYPT FAILED]"
        }
    }

    private fun startDiscoveryListener() {
        UdpDiscovery.listen { packet ->
            val discoveryResult =
                peerDiscoveryHandler.handleDiscoveredNode(
                    peerId = packet.sourceNodeId,
                    ipAddress = packet.sourceIp,
                    publicKey = packet.publicKey,
                    gateway = packet.gateway,
                    relay = packet.relay,
                    sourceGlobalId = packet.sourceGlobalId,
                    sourcePublicKeyHash = packet.sourcePublicKeyHash
                )

            runOnUiThread {
                appendLogRaw("\nNODE ${packet.sourceNodeId} @ ${packet.sourceIp}")
                appendLogRaw(
                    "\nIDENTITY ${
                        IdentityDisplayFormatter.primaryLabel(
                            canonicalDisplayName = discoveryResult.identityRecord.displayName,
                            walletAddress = discoveryResult.identityRecord.walletAddress,
                            globalId = discoveryResult.identityRecord.globalId,
                            publicKey = discoveryResult.identityRecord.publicKey,
                            legacyName = packet.sourceNodeId,
                            ipAddress = discoveryResult.identityRecord.lastKnownIp ?: packet.sourceIp
                        )
                    }${
                        IdentityDisplayFormatter.secondaryLabel(
                            primaryLabel = IdentityDisplayFormatter.primaryLabel(
                                canonicalDisplayName = discoveryResult.identityRecord.displayName,
                                walletAddress = discoveryResult.identityRecord.walletAddress,
                                globalId = discoveryResult.identityRecord.globalId,
                                publicKey = discoveryResult.identityRecord.publicKey,
                                legacyName = packet.sourceNodeId,
                                ipAddress = discoveryResult.identityRecord.lastKnownIp ?: packet.sourceIp
                            ),
                            legacyName = packet.sourceNodeId,
                            walletAddress = discoveryResult.identityRecord.walletAddress,
                            globalId = discoveryResult.identityRecord.globalId,
                            publicKey = discoveryResult.identityRecord.publicKey,
                            ipAddress = discoveryResult.identityRecord.lastKnownIp ?: packet.sourceIp
                        )?.let { " | $it" } ?: ""
                    }"
                )

                if (discoveryResult.peerKeyChanged) {
                    val fingerprint =
                        CryptoEngine.fingerprint(packet.publicKey)

                    appendLogRaw(
                        "\nWARNING: KEY CHANGED for ${packet.sourceNodeId} ($fingerprint)"
                    )

                    Toast.makeText(
                        this,
                        "Peer key changed: ${packet.sourceNodeId}",
                        Toast.LENGTH_LONG
                    ).show()
                }

                requestRefreshUi()
                MeshRuntimeManager.recordDiscovery(discoveryResult.discoveredNode)
            }
        }
    }

    private fun observeRuntimeState() {
        if (runtimeObserversStarted) {
            return
        }
        runtimeObserversStarted = true
        lifecycleScope.launch {
            MeshRuntimeManager.aliveNodes.collectLatest { count ->
                runOnUiThread {
                    txtNodes.text = count.toString()
                    if (count > 0) {
                        RuntimeUiStateManager.clearTransientState("main:startMesh")
                    }
                    Log.d("GHALBIT-CONTACT-OBSERVER", "aliveNodes=$count")
                }
            }
        }
        lifecycleScope.launch {
            SosAlertManager.alerts.collectLatest { alerts ->
                val latest = alerts.firstOrNull() ?: return@collectLatest
                runOnUiThread {
                    txtUserMessage.text =
                        getString(
                            R.string.sos_banner_message,
                            latest.sourceNodeId,
                            latest.sourceGlobalId ?: "-",
                            latest.message
                        )
                    runtimeSoftBanner.showMessage(
                        key = "sos:${latest.alertId}",
                        title = "SOS masuk dari ${latest.sourceNodeId}",
                        detail = latest.message.ifBlank { "Sinyal darurat diterima." },
                        priority = 6,
                        durationMs = 4000L,
                        miniStatus = "SOS aktif",
                        actionLabel = "BUKA",
                        action = {
                            startActivity(Intent(this@MainActivity, SosInboxActivity::class.java))
                        }
                    )
                }
            }
        }
    }

    private fun observeRuntimeUiState() {
        lifecycleScope.launch {
            RuntimeUiStateManager.stateFlow.collectLatest { snapshot ->
                runtimeLoadingOverlay.render(snapshot)
                runtimeSoftBanner.render(snapshot)
                applyMainRuntimeSnapshot(snapshot)
            }
        }
    }

    private fun applyMainRuntimeSnapshot(snapshot: com.ghalbitnet.meshx2.ui.RuntimeUiSnapshot) {
        val runtimeKey = "${snapshot.state.name}|${snapshot.detail}|${snapshot.actionsLocked}"
        if (runtimeKey == lastAppliedRuntimeKey) {
            Log.d("GHALBIT-SCROLL-AUDIT", "skipped same state")
            return
        }
        val isBlockingState =
            snapshot.state in setOf(
                RuntimeUiState.PREPARING,
                RuntimeUiState.CONNECTING,
                RuntimeUiState.VERIFYING,
                RuntimeUiState.SYNCING
            )
        val now = System.currentTimeMillis()
        if (isMainScrollActive && !isBlockingState && now - lastMainScrollAt < 700L) {
            Log.d("GHALBIT-SCROLL-AUDIT", "throttled while scrolling")
            return
        }
        runOnUiThread {
            if (txtUserMessage.text.toString() != snapshot.detail) {
                txtUserMessage.text = snapshot.detail
            }
            val nextStatusLabel = snapshot.state.name
            if (lastAppliedStatusLabel != nextStatusLabel) {
                txtStatus.text = nextStatusLabel
                lastAppliedStatusLabel = nextStatusLabel
            }
            if (lastProgressBusy != isBlockingState) {
                progressMain.visibility = if (isBlockingState) View.VISIBLE else View.INVISIBLE
                lastProgressBusy = isBlockingState
                Log.d("GHALBIT-BLAST-GUARD", "reduced relayout source=progressMain")
            }
            applyActionLock(snapshot.actionsLocked)
            lastAppliedRuntimeKey = runtimeKey
            Log.d("GHALBIT-SCROLL-AUDIT", "applied state")
            Log.d("GHALBIT-UX", "main state=${snapshot.state} locked=${snapshot.actionsLocked}")
        }
    }

    private fun applyActionLock(locked: Boolean) {
        findViewById<Button>(R.id.btnChat).isEnabled = !locked
        findViewById<Button>(R.id.btnSOS).isEnabled = !locked
        findViewById<Button>(R.id.btnRuntimeDashboard).isEnabled = true
        Log.d("GHALBIT-ACTION-LOCK", "main locked=$locked")
    }

    private fun initializeMeshFeatures() {
        wifiDirect = WifiDirectManager(this)
        nearby = NearbyManager(this, keyStore)
        MeshRuntimeManager.markTransportState("WiFi Direct", true)
        MeshRuntimeManager.markTransportState("Nearby", true)
    }

    private fun initializeButtons() {
        if (buttonsInitialized) {
            return
        }

        buttonsInitialized = true

        findViewById<Button>(R.id.btnMesh).setOnClickListener {
            if (!ActionDebounceManager.allow("main:mesh", runtimeBusy = false, cooldownMs = 1200L)) {
                return@setOnClickListener
            }
            if (!allPermissionsGranted()) {
                requestPermissionsIfNeeded()
                setUserMessage(getString(R.string.permission_needed_simple), false)
                appendLog("Menunggu izin Android dari pengguna.")
                Toast.makeText(this, getString(R.string.permission_needed_simple), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!meshStarted) {
                startMesh()
                return@setOnClickListener
            }

            txtStatus.text = getString(R.string.status_online)
            appendLogRaw("\n")
            appendLogRaw(getString(R.string.mesh_enabled))
            appendLogRaw("\n")
            appendLogRaw(MeshHealthReporter.report())

            requestRefreshUi(0L)
            startAutoRecovery()
        }

        findViewById<Button>(R.id.btnChat).setOnClickListener {
            if (!ActionDebounceManager.allow("main:chat", RuntimeUiStateManager.current().actionsLocked)) {
                return@setOnClickListener
            }
            SafeNavigator.open(
                this,
                ContactListActivity::class.java,
                "Chat belum tersedia"
            )
        }

        findViewById<Button>(R.id.btnSavedContacts).setOnClickListener {
            SafeNavigator.open(
                this,
                SavedContactsActivity::class.java,
                "Kontak tersimpan belum tersedia"
            )
        }

        findViewById<Button>(R.id.btnMyProfile).setOnClickListener {
            SafeNavigator.open(
                this,
                MyProfileActivity::class.java,
                "Kartu nama saya belum tersedia"
            )
        }

        findViewById<Button>(R.id.btnRemoteMode).setOnClickListener {
            SafeNavigator.open(
                this,
                RemoteModeActivity::class.java,
                "Mode jarak jauh belum tersedia"
            )
        }

        findViewById<Button>(R.id.btnNetwork).setOnClickListener {
            SafeNavigator.open(
                this,
                NetworkActivity::class.java,
                "Network Monitor belum tersedia"
            )
        }

        findViewById<Button>(R.id.btnRuntimeDashboard).setOnClickListener {
            if (!ActionDebounceManager.allow("main:dashboard", runtimeBusy = false, cooldownMs = 700L)) {
                return@setOnClickListener
            }
            SafeNavigator.open(
                this,
                RuntimeDashboardActivity::class.java,
                "Runtime Dashboard belum tersedia"
            )
        }

        findViewById<Button>(R.id.btnNotifications).setOnClickListener {
            SafeNavigator.open(
                this,
                NotificationSettingsActivity::class.java,
                "Pengaturan notifikasi belum tersedia"
            )
        }

        findViewById<Button>(R.id.btnMediaSettings).setOnClickListener {
            SafeNavigator.open(
                this,
                ChatMediaSettingsActivity::class.java,
                "Pengaturan chat dan media belum tersedia"
            )
        }

        findViewById<Button>(R.id.btnDebug).setOnClickListener {
            SafeNavigator.open(
                this,
                DebugActivity::class.java,
                "Debug Panel belum tersedia"
            )
        }

        findViewById<Button>(R.id.btnSOS).setOnClickListener {
            if (!ActionDebounceManager.allow("main:sos", RuntimeUiStateManager.current().actionsLocked, cooldownMs = 1800L)) {
                return@setOnClickListener
            }
            sendSos()
        }

        findViewById<Button>(R.id.btnMint).setOnClickListener {
            SafeNavigator.open(
                this,
                MeshEconomyActivity::class.java,
                "Pusat ekonomi jasa belum tersedia"
            )
        }
    }
    private fun sendAck(
        packet: MeshPacket
    ) {
        try {
            val peerIp =
                keyStore.getPeerAddress(packet.source) ?: return

            val ackPacket =
                MeshPacket(
                    packetId = "ACK-" + System.currentTimeMillis(),
                    source = myPeerId,
                    destination = packet.source,
                    type = "ACK",
                    payload = packet.packetId,
                    encrypted = false
                )

            MeshSocketClient.send(
                peerIp,
                ackPacket
            )

            MeshStatistics.sentPacket("ACK")

            runOnUiThread {
                appendLogRaw(
                    "\nACK sent to ${packet.source}"
                )
            }

        } catch (e: Exception) {
            runOnUiThread {
                appendLogRaw(
                    "\nACK failed: ${e.message}"
                )
            }
        }
    }

    private fun sendRouteAck(targetPeerId: String, ackFor: String) {
        val peerIp = keyStore.getPeerAddress(targetPeerId) ?: return
        val payload = JSONObject().put("ackFor", ackFor).toString()
        val ackPacket = MeshPacket(
            packetId = "ROUTE_ACK-${System.currentTimeMillis()}",
            source = myPeerId,
            destination = targetPeerId,
            type = "ROUTE_ACK",
            payload = payload,
            encrypted = false
        )
        MeshSocketClient.send(peerIp, ackPacket)
    }

    private fun sendVoiceAck(targetPeerId: String, ackFor: String, callId: String, audioPath: String) {
        val peerIp = keyStore.getPeerAddress(targetPeerId) ?: return
        val payload = JSONObject()
            .put("ackFor", ackFor)
            .put("callId", callId)
            .put("audioPath", audioPath)
            .toString()
        val ackPacket = MeshPacket(
            packetId = "VOICE_ACK-${System.currentTimeMillis()}",
            source = myPeerId,
            destination = targetPeerId,
            type = "VOICE_ACK",
            payload = payload,
            encrypted = false
        )
        MeshSocketClient.send(peerIp, ackPacket)
    }

    private fun sendSos() {
        RuntimeUiStateManager.onSosSending(true)
        setUserMessage(getString(R.string.sending_sos), true)
        appendLogRaw("\nSOS SIGNAL SENT")

        Toast.makeText(
            this,
            getString(R.string.sos_active),
            Toast.LENGTH_SHORT
        ).show()

        val nodes = DiscoveryManager.discoverNodes()
        val sosPacket =
            MeshPacket(
                packetId = "SOS-" + System.currentTimeMillis(),
                source = myPeerId,
                destination = "BROADCAST",
                type = "SOS",
                payload = "EMERGENCY"
            )

        nodes.forEach { node ->
            try {
                val identityRecord =
                    IdentityRegistry.upsert(
                        IdentityBridge.fromMeshNode(node)
                    )

                com.ghalbitnet.meshx2.chat.ConversationIdentityStore.upsert(
                    context = applicationContext,
                    chatId = node.name,
                    identity = identityRecord
                )

                MeshStatistics.sentPacket("SOS")

                val sent = MeshSocketClient.sendBlocking(node.ipAddress, sosPacket)
                if (sent) {
                    AdaptiveRouteManager.recordRouteEvidence(
                        chatId = node.name,
                        globalId = identityRecord.globalId,
                        nextHop = node.ipAddress,
                        transport = "LOCAL_MESH_DIRECT",
                        source = RouteEvidenceSource.SOS,
                        confidence = 95
                    )
                }
            } catch (e: Exception) {
                appendLogRaw("\nSOS failed to ${node.name}")
            }
        }

        if (nodes.isEmpty() && OnlinePresenceManager.hasInternet(applicationContext)) {
            lifecycleScope.launch(Dispatchers.IO) {
                val selfGlobalId = GlobalMeshIdentityManager.buildGlobalId(keyStore.publicKeyBase64)
                val selfHash = com.ghalbitnet.meshx2.call.CallManager.publicKeyHash(keyStore.publicKeyBase64)
                val onlineTargets = OnlinePresenceManager.all(applicationContext).filter { it.online && it.globalId != selfGlobalId }
                onlineTargets.forEach { presence ->
                    presence.route?.let { route ->
                        val ok =
                            OnlineFallbackTransport.sendSosViaInternet(
                                context = applicationContext,
                                route = route,
                                sourceNodeId = myPeerId,
                                sourceGlobalId = selfGlobalId,
                                sourcePublicKeyHash = selfHash,
                                sourcePublicKey = keyStore.publicKeyBase64,
                                message = "EMERGENCY"
                            )
                        Log.d("GHALBIT-DELIVERY", "target=${presence.globalId} local=false online=true route=internet_sos sent=$ok")
                    }
                }
            }
        }

        setUserMessage("SOS selesai dikirim ke node yang tersedia.", false)
        RuntimeUiStateManager.onSosSending(false)
    }

    private fun updateUI() {
        val nodes = DiscoveryManager.discoverNodes()
        NetworkTruthProbe.maybeSend(applicationContext, myPeerId, nodes.firstOrNull { it.online })
        val preferredNode =
            nodes.firstOrNull { it.online }
        val connectionSnapshot =
            ConnectivityStatusDetector.snapshot(this, nodes)
        val hybridSnapshot =
            HybridConnectivityPlanner.snapshot(this, nodes)
        val gatewayCount =
            nodes.count { it.online && it.gateway } +
                if (connectionSnapshot.hasInternet) 1 else 0
        val gatewaySummary =
            InternetGatewayRegistry.summaryText(this, nodes)

        MeshStatistics.updateOnlineNodes(NodeStatusManager.onlineCount())
        MeshRuntimeState.updateNodeCount(nodes.count { it.online })
        MeshRuntimeState.updateGatewaySummary(gatewaySummary)
        txtNodes.text = nodes.size.toString()
        txtConnectionScope.text = connectionSnapshot.title(this)
        txtHybridStatus.text = hybridSnapshot.title
        setUserMessage(getString(R.string.checking_network), true)

        pingUpdateJob?.cancel()
        pingUpdateJob = lifecycleScope.launch(Dispatchers.IO) {
            val latencies =
                nodes
                    .filter { it.online }
                    .map { LatencyEngine.calculateLatency(it.ipAddress) }
                    .filter { it >= 0 }

            val pingText =
                if (latencies.isEmpty()) {
                    "--"
                } else {
                    "${latencies.average().toInt()} ms"
                }

            runOnUiThread {
                txtPing.text = pingText
                setUserMessage(
                    if (nodes.isEmpty()) {
                        connectionSnapshot.description(this@MainActivity)
                    } else {
                        buildConnectionStatusMessage(
                            nodes.size,
                            preferredNode?.ipAddress,
                            connectionSnapshot,
                            gatewayCount,
                            gatewaySummary
                        )
                    },
                    false
                )
            }
        }

        nodes.forEach {
            MeshRegistry.updateNode(it)
        }

        updateBalance()

        
    }

    private fun buildConnectionStatusMessage(
        nodeCount: Int,
        preferredAddress: String?,
        connectionSnapshot: ConnectivityStatusDetector.Snapshot,
        gatewayCount: Int,
        gatewaySummary: String
    ): String {
        val prefix =
            "Jaringan diperbarui. $nodeCount node terdeteksi."

        val suffix =
            when (TransportPreference.modeForAddress(preferredAddress ?: "")) {
                TransportPreference.Mode.LAN_HOTSPOT ->
                    getString(R.string.transport_lan_hotspot)

                TransportPreference.Mode.DIRECT_IP ->
                    getString(R.string.transport_direct_ip)

                TransportPreference.Mode.NEARBY ->
                    getString(R.string.transport_nearby_fallback)

                TransportPreference.Mode.UNKNOWN ->
                    getString(R.string.transport_other_fallback)
            }

        val gatewayText =
            when {
                connectionSnapshot.hasInternet ->
                    getString(R.string.gateway_provider_local)

                gatewayCount > 0 ->
                    resources.getQuantityString(
                        R.plurals.gateway_provider_remote_count,
                        gatewayCount,
                        gatewayCount
                    )

                else ->
                    getString(R.string.gateway_provider_none)
            }

        return "$prefix ${connectionSnapshot.description(this)} $gatewayText $gatewaySummary $suffix"
    }

    private fun broadcastLocalNode() {
        val localGateway =
            ConnectivityStatusDetector.localGatewayActive(this)

        UdpDiscovery.broadcastNode(
            nodeName = myPeerId,
            gateway = localGateway,
            relay = true
        )
    }

    private fun requestRefreshUi(
        delayMs: Long = UI_REFRESH_DEBOUNCE_MS
    ) {
        uiRefreshJob?.cancel()
        uiRefreshJob =
            lifecycleScope.launch {
                delay(delayMs)
                updateUI()
            }
    }

    @SuppressLint("SetTextI18n")
    private fun updateBalance() {
        lifecycleScope.launch(Dispatchers.IO) {
            val walletGlobalId =
                GlobalMeshIdentityManager.buildGlobalId(keyStore.publicKeyBase64)

            TokenManager.ensureWalletBootstrap(walletGlobalId)

            val chainBalance =
                ledger.getBalance(myPeerId)

            val walletBalance =
                TokenManager.getLocalWalletBalance(walletGlobalId)

            runOnUiThread {
                txtBalance.text = "%.2f GHBT".format(chainBalance + walletBalance)
            }
        }
    }

    private fun getLocalIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()

            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()

                if (networkInterface.isLoopback || !networkInterface.isUp) {
                    continue
                }

                val name = networkInterface.name.lowercase()

                if (
                    name.startsWith("rmnet") ||
                    name.startsWith("ccmni") ||
                    name.startsWith("pdp")
                ) {
                    continue
                }

                val addresses = networkInterface.inetAddresses

                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()

                    if (
                        address is Inet4Address &&
                        !address.isLoopbackAddress
                    ) {
                        return address.hostAddress
                    }
                }
            }

            null
        } catch (e: Exception) {
            Log.e("GHALBIT", "IP error", e)
            null
        }
    }

    private fun runtimePermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        return permissions
    }

    private fun criticalMeshPermissions(): List<String> {
        return runtimePermissions()
            .filterNot {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    it == Manifest.permission.POST_NOTIFICATIONS
            }
    }

    private fun requestPermissionsIfNeeded() {
        val missing =
            runtimePermissions().filter {
                ContextCompat.checkSelfPermission(
                    this,
                    it
                ) != PackageManager.PERMISSION_GRANTED
            }

        if (missing.isNotEmpty()) {
            setUserMessage(getString(R.string.permission_needed_simple), false)
            ActivityCompat.requestPermissions(
                this,
                missing.toTypedArray(),
                1001
            )
        }
    }

    private fun allPermissionsGranted(): Boolean {
        return criticalMeshPermissions().all {
            ContextCompat.checkSelfPermission(
                this,
                it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == 1001 && allPermissionsGranted()) {
            setUserMessage(getString(R.string.permission_granted_starting), true)
            startMesh()
        } else if (requestCode == 1001) {
            setUserMessage(getString(R.string.permission_denied_simple), false)
            Toast.makeText(
                this,
                getString(R.string.permission_denied_simple),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        runtimeLoadingOverlay.onHostResume()
        runtimeSoftBanner.onHostResume()

        LocalBroadcastManager
            .getInstance(this)
            .registerReceiver(
                fileTransferStatusReceiver,
                IntentFilter(FileTransferManager.ACTION_TRANSFER_STATUS)
            )
    }

    override fun onPause() {
        LocalBroadcastManager
            .getInstance(this)
            .unregisterReceiver(fileTransferStatusReceiver)
        runtimeLoadingOverlay.onHostPause()
        runtimeSoftBanner.onHostPause()

        super.onPause()
    }

    override fun onDestroy() {
        pingUpdateJob?.cancel()
        pingUpdateJob = null
        uiRefreshJob?.cancel()
        uiRefreshJob = null
        stopRouteCleaner()
        scrollStateHandler.removeCallbacks(clearScrollStateRunnable)
        runtimeLoadingOverlay.onHostDestroy()
        runtimeSoftBanner.onHostDestroy()
        NetworkHandoffMonitor.stop(applicationContext)
        super.onDestroy()
    }
}
