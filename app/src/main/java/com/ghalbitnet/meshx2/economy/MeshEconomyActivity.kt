package com.ghalbitnet.meshx2.economy

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.core.log.MeshLogger
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.core.network.InternetGatewayRegistry
import com.ghalbitnet.meshx2.core.server.FirebaseRemoteSyncManager
import com.ghalbitnet.meshx2.discovery.DiscoveryManager
import com.ghalbitnet.meshx2.security.KeyStoreManager
import com.ghalbitnet.meshx2.token.TokenManager
import com.ghalbitnet.meshx2.vpn.UsageHistoryActivity
import com.ghalbitnet.meshx2.vpn.VpnLogManager
import com.ghalbitnet.meshx2.vpn.VpnController
import com.ghalbitnet.meshx2.vpn.VpnOperatingMode
import com.ghalbitnet.meshx2.vpn.VpnStatusProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MeshEconomyActivity : AppCompatActivity() {

    private lateinit var keyStore: KeyStoreManager
    private lateinit var globalId: String

    private lateinit var txtEconomyWallet: TextView
    private lateinit var txtEconomySessions: TextView
    private lateinit var txtEconomyTraffic: TextView
    private lateinit var txtEconomyBurned: TextView
    private lateinit var txtEconomyGateway: TextView
    private lateinit var txtEconomyRelay: TextView
    private lateinit var txtEconomyBuilder: TextView
    private lateinit var txtEconomyValidator: TextView
    private lateinit var txtEconomyReserve: TextView
    private lateinit var txtEconomyPolicy: TextView
    private lateinit var txtEconomyAutoRole: TextView
    private lateinit var txtEconomyAppAccess: TextView
    private lateinit var txtEconomyBridge: TextView
    private lateinit var txtEconomyBridgeLoad: TextView
    private lateinit var txtDesktopTest: TextView
    private lateinit var txtEconomyRecent: TextView
    private lateinit var btnEconomyLedger: Button
    private lateinit var btnEconomyPeerRanking: Button
    private lateinit var btnUsageHistory: Button
    private lateinit var btnBridgeStart: Button
    private lateinit var btnBridgeStop: Button
    private lateinit var btnDesktopTestStart: Button
    private lateinit var btnDesktopTestStop: Button
    private var bridgeRefreshJob: Job? = null
    @Volatile
    private var refreshInFlight: Boolean = false
    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                startVpnSafely()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.ghalbit_vpn_permission_required),
                    Toast.LENGTH_SHORT
                ).show()
            }
            renderImmediateState()
            refreshUi()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mesh_economy)

        keyStore = KeyStoreManager(this)
        globalId = GlobalMeshIdentityManager.buildGlobalId(keyStore.publicKeyBase64)
        TokenManager.init(this)

        txtEconomyWallet = findViewById(R.id.txtEconomyWallet)
        txtEconomySessions = findViewById(R.id.txtEconomySessions)
        txtEconomyTraffic = findViewById(R.id.txtEconomyTraffic)
        txtEconomyBurned = findViewById(R.id.txtEconomyBurned)
        txtEconomyGateway = findViewById(R.id.txtEconomyGateway)
        txtEconomyRelay = findViewById(R.id.txtEconomyRelay)
        txtEconomyBuilder = findViewById(R.id.txtEconomyBuilder)
        txtEconomyValidator = findViewById(R.id.txtEconomyValidator)
        txtEconomyReserve = findViewById(R.id.txtEconomyReserve)
        txtEconomyPolicy = findViewById(R.id.txtEconomyPolicy)
        txtEconomyAutoRole = findViewById(R.id.txtEconomyAutoRole)
        txtEconomyAppAccess = findViewById(R.id.txtEconomyAppAccess)
        txtEconomyBridge = findViewById(R.id.txtEconomyBridge)
        txtEconomyBridgeLoad = findViewById(R.id.txtEconomyBridgeLoad)
        txtDesktopTest = findViewById(R.id.txtDesktopTest)
        txtEconomyRecent = findViewById(R.id.txtEconomyRecent)
        btnEconomyLedger = findViewById(R.id.btnEconomyLedger)
        btnEconomyPeerRanking = findViewById(R.id.btnEconomyPeerRanking)
        btnUsageHistory = findViewById(R.id.btnUsageHistory)
        btnBridgeStart = findViewById(R.id.btnBridgeStart)
        btnBridgeStop = findViewById(R.id.btnBridgeStop)
        btnDesktopTestStart = findViewById(R.id.btnDesktopTestStart)
        btnDesktopTestStop = findViewById(R.id.btnDesktopTestStop)
        renderImmediateState()

        btnEconomyLedger.setOnClickListener {
            startActivity(Intent(this, MeshEconomyLedgerActivity::class.java))
        }

        btnEconomyPeerRanking.setOnClickListener {
            startActivity(Intent(this, PeerRankingActivity::class.java))
        }

        btnUsageHistory.setOnClickListener {
            startActivity(Intent(this, UsageHistoryActivity::class.java))
        }

        btnBridgeStart.setOnClickListener {
            beginBridgeStart(VpnOperatingMode.MONITORING_PASSIVE)
        }

        btnBridgeStop.setOnClickListener {
            val bridgeState = InternetBridgeStateManager.snapshot(this)
            if (!bridgeState.canStop) {
                Toast.makeText(
                    this,
                    "Layanan internet luar belum sedang aktif.",
                    Toast.LENGTH_SHORT
                ).show()
                renderImmediateState()
                refreshUi()
                return@setOnClickListener
            }
            stopVpnSafely()
            renderImmediateState()
            refreshUi()
        }

        btnBridgeStart.setOnLongClickListener {
            beginBridgeStart(VpnOperatingMode.MONITORING_LIGHT)
            true
        }

        btnDesktopTestStart.setOnClickListener {
            lifecycleScope.launch {
                val result = DesktopInternetTestManager.start(this@MeshEconomyActivity, globalId)
                Toast.makeText(this@MeshEconomyActivity, result.detail, Toast.LENGTH_SHORT).show()
                refreshUi()
            }
        }

        btnDesktopTestStop.setOnClickListener {
            val result =
                DesktopInternetTestManager.stop(
                    this,
                    getString(R.string.desktop_test_stopped_manual)
                )
            Toast.makeText(this, result.detail, Toast.LENGTH_SHORT).show()
            refreshUi()
        }

        lifecycleScope.launch {
            delay(250L)
            refreshUi()
        }
    }

    override fun onResume() {
        super.onResume()
        renderImmediateState()
        bridgeRefreshJob?.cancel()
        bridgeRefreshJob = lifecycleScope.launch {
            delay(500L)
            refreshUi()
            while (true) {
                delay(4_000L)
                refreshUi()
            }
        }
    }

    override fun onPause() {
        bridgeRefreshJob?.cancel()
        bridgeRefreshJob = null
        super.onPause()
    }

    private fun refreshUi() {
        if (refreshInFlight) return
        refreshInFlight = true
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                FirebaseRemoteSyncManager.refreshControlData(
                    this@MeshEconomyActivity,
                    setOf(globalId)
                )
                TokenManager.ensureWalletBootstrap(globalId)

                val walletBalance =
                    TokenManager.getLocalWalletBalance(globalId)
                val walletRole =
                    FirebaseRemoteSyncManager.cachedWalletOwnerClass(this@MeshEconomyActivity, globalId)
                        ?: EconomyRoleManager.classify(globalId).name
                val walletRoleLabel =
                    EconomyRoleManager.displayName(this@MeshEconomyActivity, walletRole)

                val builderBalance =
                    TokenManager.getBuilderWalletBalance()

                val snapshot =
                    MeshServiceLedger.snapshot(this@MeshEconomyActivity)
                val autoRole =
                    AutoNodeRoleManager.currentDevice(
                        context = this@MeshEconomyActivity,
                        economySnapshot = snapshot,
                        localWalletRoleName = walletRole
                    )
                val nodes =
                    DiscoveryManager.discoverNodes()

                val policy =
                    MeshEconomyServerPolicyManager.current(this@MeshEconomyActivity)

                val appAccess =
                    AppServiceAccessPolicyManager.evaluate(this@MeshEconomyActivity, nodes)

                val bridgePolicy =
                    InternetBridgePolicyManager.current(this@MeshEconomyActivity)

                val bridgeDecision =
                    InternetBridgePolicyManager.evaluate(this@MeshEconomyActivity, globalId)

                val peerPolicySummary =
                    InternetBridgePeerPolicyManager.summary(this@MeshEconomyActivity)

                val requestSummary =
                    InternetBridgeRequestLogManager.summary(this@MeshEconomyActivity)

                val queueSummary =
                    InternetBridgeRequestQueueManager.reevaluate(this@MeshEconomyActivity)

                val bridgeSnapshot =
                    InternetBridgeUsageMonitor.snapshot(this@MeshEconomyActivity)
                val desktopTest =
                    DesktopInternetTestManager.reevaluate(this@MeshEconomyActivity, globalId)

                val bridgeState =
                    InternetBridgeStateManager.snapshot(this@MeshEconomyActivity)

                val gatewayCandidates =
                    InternetGatewayRegistry.candidates(this@MeshEconomyActivity, nodes)

                val routePlans =
                    InternetRoutePlanner.plans(this@MeshEconomyActivity, nodes)

                val routeLoadText =
                    buildString {
                    val gateways =
                        if (gatewayCandidates.isEmpty()) {
                            getString(R.string.internet_bridge_load_none)
                        } else {
                            gatewayCandidates.take(3).joinToString("\n") { gateway ->
                                val gatewayLabel =
                                    if (gateway.isLocal) {
                                        getString(R.string.gateway_this_device)
                                    } else {
                                        gateway.name
                                    }
                                val presentation =
                                    InternetGatewayHealthManager.present(
                                        context = this@MeshEconomyActivity,
                                        gateway = gateway,
                                        selectedGatewayId = bridgeDecision.gatewayId
                                    )
                                "${presentation.signalIndicator} $gatewayLabel\n${presentation.statusLabel} - ${presentation.summaryLabel}"
                            }
                        }
                    val routes =
                        if (routePlans.isEmpty()) {
                            getString(R.string.internet_bridge_load_none)
                        } else {
                            routePlans.take(3).joinToString("\n") { route ->
                                val pathLabel =
                                    if (route.relayPath.isEmpty()) {
                                        getString(R.string.service_economy_path_direct)
                                    } else {
                                        route.relayPath.joinToString(" -> ") { it.nodeName }
                                    }
                                val routeLoad =
                                    InternetRouteCooperationManager.activeLoad(
                                        this@MeshEconomyActivity,
                                        route.routeKey
                                    )
                                val routeState =
                                    when {
                                        route.gateway.nodeId == bridgeDecision.gatewayId ->
                                            getString(R.string.internet_bridge_health_active_now)
                                        routeLoad > 0 ->
                                            getString(R.string.internet_bridge_health_busy)
                                        else ->
                                            getString(R.string.internet_bridge_health_backup_ready)
                                    }
                                "${route.gateway.name} - $routeState\n$pathLabel"
                            }
                        }
                    append(
                        getString(
                            R.string.internet_bridge_load_value,
                            gateways,
                            routes
                        )
                    )
                }

                val recent =
                    MeshServiceLedger.recentEntries(this@MeshEconomyActivity, 4)

                val recentText =
                    if (recent.isEmpty()) {
                        getString(R.string.service_economy_recent_empty)
                    } else {
                        recent.joinToString("\n\n") { entry ->
                            val time =
                                SimpleDateFormat("HH:mm", Locale.getDefault())
                                    .format(Date(entry.session.endedAt))

                            "$time - ${entry.settlement.notes}\n" +
                                getString(
                                    R.string.service_economy_recent_line,
                                    entry.settlement.burnAmount,
                                    entry.settlement.gatewayReward,
                                    entry.settlement.totalRelayReward
                                )
                        }
                    }

                runOnUiThread {
                    txtEconomyWallet.text =
                        getString(R.string.service_economy_wallet_role_value, walletBalance, walletRoleLabel)
                txtEconomySessions.text =
                    getString(R.string.service_economy_sessions_value, snapshot.sessionCount)
                txtEconomyTraffic.text =
                    getString(
                        R.string.service_economy_traffic_value,
                        snapshot.totalBytes / 1024.0 / 1024.0
                    )
                txtEconomyBurned.text =
                    getString(R.string.service_economy_burned_value, snapshot.totalBurned)
                txtEconomyGateway.text =
                    getString(R.string.service_economy_gateway_value, snapshot.totalGatewayReward)
                txtEconomyRelay.text =
                    getString(R.string.service_economy_relay_value, snapshot.totalRelayReward)
                txtEconomyBuilder.text =
                    getString(
                        R.string.service_economy_builder_value,
                        snapshot.totalBuilderReward,
                        builderBalance
                    )
                txtEconomyValidator.text =
                    getString(R.string.service_economy_validator_value, snapshot.totalValidatorReward)
                txtEconomyReserve.text =
                    getString(R.string.service_economy_reserve_value, snapshot.totalTreasury)
                txtEconomyPolicy.text =
                    getString(
                        R.string.service_economy_policy_role_value,
                        policy.sourceLabel,
                        policy.versionLabel,
                        walletRoleLabel,
                        policy.priceReferencePerGbhtIdr
                    )
                txtEconomyAutoRole.text =
                    getString(
                        R.string.auto_role_value,
                        autoRole.title,
                        autoRole.trustScore,
                        autoRole.contributionScore,
                        autoRole.detail
                    )
                txtEconomyAppAccess.text =
                    getString(
                        R.string.app_service_access_value,
                        appAccess.title,
                        appAccess.detail,
                        appAccess.rewardLabel
                    )
                txtEconomyBridge.text =
                    getString(
                        R.string.internet_bridge_monitor_with_user_policy,
                        bridgeState.state.name,
                        bridgeState.detail,
                        bridgePolicy.versionLabel,
                        bridgePolicy.maxSessionMinutes,
                        bridgePolicy.maxSessionMb,
                        bridgeDecision.userTier.name,
                        bridgeDecision.walletBalance,
                        bridgeDecision.dailyUsedMb,
                        bridgeDecision.dailyQuotaMb,
                        peerPolicySummary.priorityCount,
                        peerPolicySummary.blockedCount,
                        requestSummary.allowed,
                        requestSummary.denied,
                        queueSummary.activeAlias.ifBlank { "-" },
                        queueSummary.waitingCount,
                        queueSummary.deniedCount,
                        bridgeSnapshot.summary
                    )
                txtEconomyBridgeLoad.text = routeLoadText
                btnBridgeStart.isEnabled = true
                btnBridgeStop.isEnabled = true
                btnBridgeStart.alpha = 1.0f
                btnBridgeStop.alpha = 1.0f
                txtDesktopTest.text =
                    getString(
                        R.string.desktop_test_value,
                        desktopTest.title(this@MeshEconomyActivity),
                        desktopTest.detail,
                        desktopTest.walletBalance,
                        desktopTest.minimumBalance,
                        if (desktopTest.hotspotReady) {
                            getString(R.string.desktop_test_hotspot_ready)
                        } else {
                            getString(R.string.desktop_test_hotspot_not_ready)
                        }
                    )
                btnDesktopTestStart.isEnabled = !desktopTest.active
                btnDesktopTestStop.isEnabled = desktopTest.active || desktopTest.status == DesktopInternetTestManager.Status.ALLOWED
                    txtEconomyRecent.text = recentText
                }
            }.onFailure { error ->
                MeshLogger.e("MeshEconomy", "refreshUi gagal", error)
                runOnUiThread {
                    txtEconomyBridge.text = "Status layanan belum bisa dimuat.\n${error.message ?: "Terjadi kesalahan tak dikenal."}"
                    btnBridgeStart.isEnabled = false
                    btnBridgeStop.isEnabled = false
                    Toast.makeText(
                        this@MeshEconomyActivity,
                        "Layanan internet luar gagal dimuat: ${error.message ?: "unknown error"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }.also {
                refreshInFlight = false
            }
        }
    }

    private fun startVpnSafely() {
        VpnController.start(this)
            .onSuccess {
                renderImmediateState()
                Toast.makeText(
                    this,
                    getString(R.string.internet_bridge_monitor_started),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .onFailure { error ->
                handleVpnStartFailure(error)
            }
    }

    private fun stopVpnSafely() {
        VpnController.stop(this)
            .onSuccess {
                renderImmediateState()
                Toast.makeText(
                    this,
                    getString(R.string.internet_bridge_monitor_stopping),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .onFailure { error ->
                MeshLogger.e("MeshEconomy", "stop VPN gagal", error)
                Toast.makeText(
                    this,
                    "Gagal menghentikan layanan internet luar: ${error.message ?: "unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun handleVpnStartFailure(error: Throwable) {
        MeshLogger.e("MeshEconomy", "start VPN gagal", error)
        InternetBridgeStateManager.mark(
            this,
            InternetBridgeStateManager.BridgeState.ERROR,
            "Start VPN gagal: ${error.message ?: "unknown error"}"
        )
        Toast.makeText(
            this,
            "Gagal memulai layanan internet luar: ${error.message ?: "unknown error"}",
            Toast.LENGTH_LONG
        ).show()
        renderImmediateState()
    }

    private fun renderImmediateState() {
        val bridgeState = InternetBridgeStateManager.snapshot(this)
        val vpnStatus = VpnStatusProvider.snapshot(this)
        txtEconomyBridge.text =
            buildString {
                append("Status layanan: ")
                append(bridgeState.state.name)
                append('\n')
                append(bridgeState.detail)
                append("\n\nVPN runtime:\n")
                append("Status UI: ${vpnStatus.uiStatus}\n")
                append("Service aktif: ${vpnStatus.serviceActive}\n")
                append("Diinginkan aktif: ${vpnStatus.desiredRunning}\n")
                append("Mode: ${vpnStatus.mode ?: "-"}\n")
                append("Gateway: ${vpnStatus.gatewayName ?: "-"}\n")
                append("Paket masuk: ${vpnStatus.packetsIn ?: 0L}\n")
                append("Paket keluar: ${vpnStatus.packetsOut ?: 0L}\n")
                append("Keputusan terakhir: ${vpnStatus.lastDecision ?: "-"}")
                vpnStatus.warning?.let {
                    append("\nPeringatan: ")
                    append(it)
                }
            }
        btnBridgeStart.isEnabled = true
        btnBridgeStop.isEnabled = true
        btnBridgeStart.alpha = 1.0f
        btnBridgeStop.alpha = 1.0f
    }

    private fun beginBridgeStart(mode: VpnOperatingMode) {
        VpnOperatingMode.set(this, mode)
        val startEvent =
            if (mode == VpnOperatingMode.MONITORING_PASSIVE) {
                "START_BUTTON_MONITORING_PASSIVE"
            } else {
                "START_BUTTON_MONITORING_LIGHT"
            }
        val startDetail =
            if (mode == VpnOperatingMode.MONITORING_PASSIVE) {
                "Start biasa menyalakan monitoring pasif tanpa TUN."
            } else {
                "Start debug menyalakan monitoring ringan berbasis TUN."
            }
        VpnLogManager.info(startEvent, startDetail)
        btnBridgeStart.isEnabled = false
        txtEconomyBridge.text =
            buildString {
                append("Menyiapkan layanan internet luar...\n")
                append(
                    if (mode == VpnOperatingMode.MONITORING_PASSIVE) {
                        "Monitoring pasif dimulai tanpa izin VPN Android."
                    } else {
                        "Mode debug TUN dimulai. Jika perlu, izin VPN Android akan diminta."
                    }
                )
            }
        lifecycleScope.launch {
            val prepareIntent =
                withContext(Dispatchers.IO) {
                    VpnController.prepareIntent(this@MeshEconomyActivity)
                }
            if (prepareIntent != null) {
                runCatching {
                    vpnPermissionLauncher.launch(prepareIntent)
                }.onFailure { error ->
                    handleVpnStartFailure(error)
                }
            } else {
                startVpnSafely()
            }
            renderImmediateState()
            refreshUi()
        }
    }
}
