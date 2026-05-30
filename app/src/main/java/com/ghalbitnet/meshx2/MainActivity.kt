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
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ghalbitnet.meshx2.access.HotspotGuardManager
import com.ghalbitnet.meshx2.blockchain.BlockchainLedger
import com.ghalbitnet.meshx2.chat.ChatDatabase
import com.ghalbitnet.meshx2.chat.ContactListActivity
import com.ghalbitnet.meshx2.chat.SavedContactsActivity
import com.ghalbitnet.meshx2.core.network.ConnectivityStatusDetector
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.core.network.HybridConnectivityPlanner
import com.ghalbitnet.meshx2.core.network.InternetGatewayRegistry
import com.ghalbitnet.meshx2.core.network.InternetProviderReadinessManager
import com.ghalbitnet.meshx2.core.network.LatencyEngine
import com.ghalbitnet.meshx2.discovery.DiscoveryManager
import com.ghalbitnet.meshx2.discovery.PeerDiscoveryHandler
import com.ghalbitnet.meshx2.discovery.UdpDiscovery
import com.ghalbitnet.meshx2.economy.InternetGatewayHealthManager
import com.ghalbitnet.meshx2.economy.MeshEconomyActivity
import com.ghalbitnet.meshx2.economy.ServicePathRecorder
import com.ghalbitnet.meshx2.economy.UsageSessionRecorder
import com.ghalbitnet.meshx2.file.FileTransferManager
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.stats.MeshStatistics
import com.ghalbitnet.meshx2.core.node.NodeStatusManager
import com.ghalbitnet.meshx2.network.IncomingPacketHandler
import com.ghalbitnet.meshx2.network.MeshSocketClient
import com.ghalbitnet.meshx2.network.OutboundMeshActionHandler
import com.ghalbitnet.meshx2.routing.MeshRegistry
import com.ghalbitnet.meshx2.routing.RouteDiscovery
import com.ghalbitnet.meshx2.routing.RouteMaintenanceManager
import com.ghalbitnet.meshx2.security.KeyStoreManager
import com.ghalbitnet.meshx2.settings.OnboardingActivity
import com.ghalbitnet.meshx2.settings.OnboardingManager
import com.ghalbitnet.meshx2.settings.HotspotVerificationActivity
import com.ghalbitnet.meshx2.settings.HotspotVerificationManager
import com.ghalbitnet.meshx2.settings.SystemSettingsActivity
import com.ghalbitnet.meshx2.token.TokenManager
import com.ghalbitnet.meshx2.token.WalletActivity
import com.ghalbitnet.meshx2.core.utils.SafeNavigator
import com.ghalbitnet.meshx2.core.runtime.MeshRuntimeState
import com.ghalbitnet.meshx2.core.runtime.MeshStartupManager
import com.ghalbitnet.meshx2.core.runtime.NetworkHandoffMonitor
import com.ghalbitnet.meshx2.core.health.MeshHealthReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
    private lateinit var txtLog: TextView
    private lateinit var logScroll: NestedScrollView
    private lateinit var txtUserMessage: TextView
    private lateinit var progressMain: ProgressBar

    private lateinit var keyStore: KeyStoreManager
    private lateinit var chatDb: ChatDatabase
    private lateinit var ledger: BlockchainLedger
    private lateinit var incomingPacketHandler: IncomingPacketHandler
    private lateinit var outboundMeshActionHandler: OutboundMeshActionHandler
    private lateinit var peerDiscoveryHandler: PeerDiscoveryHandler

    private var meshStartupSession: MeshStartupManager.Session? = null
    private var routeMaintenanceJob: Job? = null
    private var pingUpdateJob: Job? = null
    private var uiRefreshJob: Job? = null
    private var meshStarted = false
    private var buttonsInitialized = false
    private var lastUserMessage: String = ""
    private var lastBusyState: Boolean = false

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

    private val hotspotGuardListener =
        object : HotspotGuardManager.HotspotGuardListener {
            override fun onWarning(message: String) {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }

            override fun onStatus(message: String) {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        }

    private val incomingPacketListener =
        object : IncomingPacketHandler.IncomingPacketListener {
            override fun onChatReceived(summary: String) {
                appendLogRaw("\nCHAT $summary")
            }

            override fun onSosReceived(summary: String) {
                Toast.makeText(this@MainActivity, summary, Toast.LENGTH_LONG).show()
                appendLogRaw("\nSOS ALERT $summary")
            }

            override fun onCallInviteReceived(summary: String) {
                appendLog(summary)
            }

            override fun onPacketStatus(message: String) {
                appendLogRaw("\n$message")
            }

            override fun onPacketError(message: String, throwable: Throwable?) {
                appendLog(message)
                if (throwable != null) {
                    Log.e("GHALBIT", message, throwable)
                }
            }
        }

    private val peerDiscoveryListener =
        object : PeerDiscoveryHandler.PeerDiscoveryListener {
            override fun onPeerDiscovered(summary: String) {
                appendLogRaw("\n$summary")
                requestRefreshUi()
            }

            override fun onPeerUpdated(summary: String) {
                appendLogRaw("\n$summary")
                requestRefreshUi()
            }

            override fun onDiscoveryStatus(message: String) {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }

            override fun onDiscoveryError(message: String, throwable: Throwable?) {
                appendLog(message)
                if (throwable != null) {
                    Log.e("GHALBIT", message, throwable)
                }
            }
        }

    private val outboundMeshActionListener =
        object : OutboundMeshActionHandler.OutboundMeshActionListener {
            override fun onActionStatus(message: String) {
                appendLogRaw("\n$message")
            }

            override fun onActionError(message: String, throwable: Throwable?) {
                appendLog(message)
                if (throwable != null) {
                    Log.e("GHALBIT", message, throwable)
                }
            }

            override fun onSosSent(summary: String) {
                setUserMessage(summary, false)
                appendLog(summary)
            }

            override fun onCallSignalSent(summary: String) {
                appendLog(summary)
            }
        }

    private val meshStartupListener =
        object : MeshStartupManager.MeshStartupListener {
            override fun onStatus(message: String) {
                appendLog(message)
            }

            override fun onError(message: String, throwable: Throwable?) {
                appendLog(message)
                if (throwable != null) {
                    Log.e("GHALBIT", message, throwable)
                }
            }

            override fun onNodeDiscovered(summary: String) {
                // Detail discovery tetap ditangani oleh callback business logic di MainActivity
            }
        }

    private val routeMaintenanceListener =
        object : RouteMaintenanceManager.RouteMaintenanceListener {
            override fun onMaintenanceStatus(message: String) {
                appendLog(message)
            }

            override fun onMaintenanceError(message: String, throwable: Throwable?) {
                appendLog(message)
                if (throwable != null) {
                    Log.e("GHALBIT", message, throwable)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (redirectIfSetupRequired()) {
            return
        }

        setContentView(R.layout.activity_main)

        initializeViews()
        initializeCore()
        initializeButtons()
        requestPermissionsIfNeeded()

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
        txtLog = findViewById(R.id.txtLog)
        logScroll = findViewById(R.id.logScroll)
        txtUserMessage = findViewById(R.id.txtUserMessage)
        progressMain = findViewById(R.id.progressMain)
        txtLog.movementMethod = ScrollingMovementMethod()
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
            txtUserMessage.text = message
            progressMain.visibility = if (busy) View.VISIBLE else View.INVISIBLE
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
        logScroll.post {
            logScroll.fullScroll(View.FOCUS_DOWN)
        }
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
        TokenManager.init(this)

        myIp = getLocalIpAddress() ?: "10.0.0.3"

        RouteDiscovery.init(this, myIp)

        ledger = BlockchainLedger.getInstance(applicationContext)

        generatePeerId()
        incomingPacketHandler =
            IncomingPacketHandler(
                context = this,
                appContext = applicationContext,
                keyStore = keyStore,
                chatDb = chatDb,
                localPeerId = myPeerId,
                scope = lifecycleScope,
                listener = incomingPacketListener,
                openIncomingCall = { intent -> startActivity(intent) }
            )
        outboundMeshActionHandler =
            OutboundMeshActionHandler(
                localPeerId = myPeerId,
                nodeProvider = { DiscoveryManager.discoverNodes() },
                listener = outboundMeshActionListener
            )
        peerDiscoveryHandler =
            PeerDiscoveryHandler(
                context = this,
                appContext = applicationContext,
                keyStore = keyStore,
                listener = peerDiscoveryListener
            )
        txtGlobalIdentity.text =
            GlobalMeshIdentityManager.buildGlobalId(keyStore.publicKeyBase64)
        routeMaintenanceJob?.cancel()
        routeMaintenanceJob =
            RouteMaintenanceManager.start(
                scope = lifecycleScope,
                listener = routeMaintenanceListener
            )
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
        ServicePathRecorder.initialize(applicationContext, myPeerId)
        UsageSessionRecorder.initialize(applicationContext)

        Log.d("GHALBIT", "Peer ID: $myPeerId")
    }

    private fun startMesh() {
        if (meshStarted) {
            requestRefreshUi(0L)
            return
        }

        meshStarted = true
        Log.d("GHALBIT", "Starting mesh")
        txtStatus.text = "STARTING"
        setUserMessage(getString(R.string.starting_mesh), true)
        appendLog("Menyiapkan layanan mesh...")
        meshStartupSession =
            MeshStartupManager.start(
                params =
                    MeshStartupManager.StartParams(
                        context = this,
                        applicationContext = applicationContext,
                        scope = lifecycleScope,
                        keyStore = keyStore,
                        localPeerId = myPeerId,
                        wireGuardAddress = "10.0.0.3/24",
                        onBroadcastLocalNode = { broadcastLocalNode() },
                        onPacket = { packet -> incomingPacketHandler.processIncomingPacket(packet) },
                        onSecurePacket = { secure -> incomingPacketHandler.handleSecurePacket(secure) },
                        onNodeFound = { peerId, ip, pubKey, gateway, relay ->
                            peerDiscoveryHandler.handleDiscoveredNode(peerId, ip, pubKey, gateway, relay)
                        }
                    ),
                listener = meshStartupListener
            )

        txtStatus.text = "ONLINE"
        setUserMessage(getString(R.string.mesh_searching), false)
        appendLog("Mesh aktif. Menunggu node terdekat...")
        requestRefreshUi(0L)
    }


    private fun initializeButtons() {
        if (buttonsInitialized) {
            return
        }

        buttonsInitialized = true

        findViewById<Button>(R.id.btnMesh).setOnClickListener {
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
          MeshStartupManager.ensureAutoRecovery { broadcastLocalNode() }
      }

        findViewById<Button>(R.id.btnChat).setOnClickListener {
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

        findViewById<Button>(R.id.btnSystemSettings).setOnClickListener {
            SafeNavigator.open(
                this,
                SystemSettingsActivity::class.java,
                getString(R.string.system_settings_unavailable)
            )
        }

        findViewById<Button>(R.id.btnSOS).setOnClickListener {
            sendSos()
        }

        findViewById<Button>(R.id.btnMint).setOnClickListener {
            SafeNavigator.open(
                this,
                MeshEconomyActivity::class.java,
                "Pusat ekonomi jasa belum tersedia"
            )
        }

        findViewById<Button>(R.id.btnWallet).setOnClickListener {
            SafeNavigator.open(
                this,
                WalletActivity::class.java,
                "Wallet belum tersedia"
            )
        }
    }
    private fun sendSos() {
        setUserMessage(getString(R.string.sending_sos), true)

        Toast.makeText(
            this,
            getString(R.string.sos_active),
            Toast.LENGTH_SHORT
        ).show()
        outboundMeshActionHandler.sendSos()
    }

    private fun updateUI() {
        val nodes = DiscoveryManager.discoverNodes()
        val handoffSnapshot = NetworkHandoffMonitor.snapshot()
        val connectionSnapshot =
            ConnectivityStatusDetector.snapshot(this, nodes)
        HybridConnectivityPlanner.snapshot(this, nodes)
        val gatewaySummary =
            InternetGatewayRegistry.summaryText(this, nodes)
        val selectedGateway =
            InternetGatewayRegistry.select(this, nodes)
        val internetLaneText =
            if (selectedGateway == null) {
                getString(R.string.main_internet_lane_none)
            } else {
                val presentation =
                    InternetGatewayHealthManager.present(
                        context = this,
                        gateway = selectedGateway,
                        selectedGatewayId = selectedGateway.nodeId
                    )
                "${presentation.signalIndicator} ${presentation.statusLabel}"
            }

        MeshStatistics.updateOnlineNodes(NodeStatusManager.onlineCount())
        MeshRuntimeState.updateNodeCount(nodes.count { it.online })
        MeshRuntimeState.updateGatewaySummary(gatewaySummary)
        txtNodes.text = nodes.size.toString()
        txtConnectionScope.text = connectionSnapshot.title(this)
        txtHybridStatus.text = internetLaneText
        txtStatus.text = buildRuntimeDiagnosticText(handoffSnapshot)
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
                        simpleConnectionMessage(connectionSnapshot)
                    } else {
                        buildSimpleStatusMessage(
                            connectionSnapshot,
                            selectedGateway
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

    private fun buildSimpleStatusMessage(
        connectionSnapshot: ConnectivityStatusDetector.Snapshot,
        selectedGateway: InternetGatewayRegistry.GatewaySelection?
    ): String {
        val handoff = NetworkHandoffMonitor.snapshot()
        if (handoff.rediscovering) {
            return "LOCAL_MESH_REDISCOVERING"
        }
        val base =
            simpleConnectionMessage(connectionSnapshot)
        if (selectedGateway == null) {
            return "$base ${getString(R.string.main_route_waiting)}"
        }
        val presentation =
            InternetGatewayHealthManager.present(
                context = this,
                gateway = selectedGateway,
                selectedGatewayId = selectedGateway.nodeId
            )
        val providerName =
            if (selectedGateway.isLocal) {
                getString(R.string.gateway_this_device)
            } else {
                selectedGateway.name
            }
        return getString(
            R.string.main_route_message,
            base,
            providerName,
            presentation.statusLabel
        )
    }

    private fun buildRuntimeDiagnosticText(
        handoff: NetworkHandoffMonitor.Snapshot
    ): String {
        val ip = handoff.currentIp.ifBlank { "-" }
        val subnet = handoff.currentSubnet.ifBlank { "-" }
        val networkType = handoff.networkType.ifBlank { "UNKNOWN" }
        val tcpRunning = if (handoff.tcpListenerRunning) "YES" else "NO"
        val routeChanged =
            if (handoff.lastRouteChange <= 0L) {
                "-"
            } else {
                handoff.lastRouteChange.toString()
            }
        return "ONLINE | $networkType | IP=$ip | SUBNET=$subnet | TCP=$tcpRunning | LAST_ROUTE_CHANGE=$routeChanged"
    }

    private fun simpleConnectionMessage(
        connectionSnapshot: ConnectivityStatusDetector.Snapshot
    ): String {
        return when {
            connectionSnapshot.hasInternet && connectionSnapshot.hasLocal ->
                getString(R.string.main_connection_ready_full)
            connectionSnapshot.hasLocal ->
                getString(R.string.main_connection_ready_local)
            connectionSnapshot.hasInternet ->
                getString(R.string.main_connection_ready_internet)
            else ->
                getString(R.string.main_connection_waiting)
        }
    }

    private fun broadcastLocalNode() {
        val localGateway =
            InternetProviderReadinessManager.shouldAdvertiseGateway(this)

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

        if (redirectIfSetupRequired()) {
            return
        }

        HotspotGuardManager.start(
            context = this,
            scope = lifecycleScope,
            listener = hotspotGuardListener,
            onVerificationRequired = {
                startActivity(Intent(this, HotspotVerificationActivity::class.java))
                finish()
            }
        )

        LocalBroadcastManager
            .getInstance(this)
            .registerReceiver(
                fileTransferStatusReceiver,
                IntentFilter(FileTransferManager.ACTION_TRANSFER_STATUS)
            )
    }

    override fun onPause() {
        HotspotGuardManager.stop(this)

        LocalBroadcastManager
            .getInstance(this)
            .unregisterReceiver(fileTransferStatusReceiver)

        super.onPause()
    }

    override fun onDestroy() {
        HotspotGuardManager.stop(this)
        MeshStartupManager.stop(this, meshStartupSession)
        meshStartupSession = null
        routeMaintenanceJob?.cancel()
        routeMaintenanceJob = null
        pingUpdateJob?.cancel()
        pingUpdateJob = null
        uiRefreshJob?.cancel()
        uiRefreshJob = null
        super.onDestroy()
    }

    private fun redirectIfSetupRequired(): Boolean {
        if (!OnboardingManager.isCompleted(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return true
        }

        val requirement = HotspotVerificationManager.currentRequirement(this)
        if (requirement == HotspotVerificationManager.Requirement.HOTSPOT_OFF) {
            HotspotVerificationManager.invalidate(this)
        }
        if (requirement != null) {
            startActivity(Intent(this, HotspotVerificationActivity::class.java))
            finish()
            return true
        }

        return false
    }

}
