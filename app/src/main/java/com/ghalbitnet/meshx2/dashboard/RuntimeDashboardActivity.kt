package com.ghalbitnet.meshx2.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.call.VoipReadinessChecker
import com.ghalbitnet.meshx2.chat.ChatDeliveryManager
import com.ghalbitnet.meshx2.chat.ContactListActivity
import com.ghalbitnet.meshx2.diagnostics.autodiag.AutoDiagnosticActivity
import com.ghalbitnet.meshx2.diagnostics.audio.AudioReportGenerator
import com.ghalbitnet.meshx2.diagnostics.audio.AudioTruthProbe
import com.ghalbitnet.meshx2.diagnostics.virtualcall.OneDeviceIncomingCallDiagnostic
import com.ghalbitnet.meshx2.sos.SosAlertManager
import com.ghalbitnet.meshx2.sos.SosInboxActivity
import com.ghalbitnet.meshx2.ui.GhalbitTheme
import com.ghalbitnet.meshx2.ui.RuntimeLoadingOverlay
import com.ghalbitnet.meshx2.ui.RuntimeSoftBannerManager
import com.ghalbitnet.meshx2.ui.RuntimeUiStateManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RuntimeDashboardActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable =
        object : Runnable {
            override fun run() {
                renderSnapshot()
                handler.postDelayed(this, 1500L)
            }
        }

    private lateinit var txtRuntimeStatus: TextView
    private lateinit var txtMeshStatus: TextView
    private lateinit var txtContactStatus: TextView
    private lateinit var txtRouteStatus: TextView
    private lateinit var txtPeerStatus: TextView
    private lateinit var txtSosStatus: TextView
    private lateinit var txtCallStatus: TextView
    private lateinit var txtTraceStatus: TextView
    private lateinit var txtErrorStatus: TextView
    private lateinit var runtimeLoadingOverlay: RuntimeLoadingOverlay
    private lateinit var runtimeSoftBanner: RuntimeSoftBannerManager
    private var renderJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_runtime_dashboard)
        GhalbitTheme.applyWindow(this, "RuntimeDashboardActivity")
        RuntimeUiStateManager.bind(applicationContext)
        runtimeLoadingOverlay = RuntimeLoadingOverlay.attach(this)
        runtimeSoftBanner = RuntimeSoftBannerManager.attach(this)
        supportActionBar?.title = getString(R.string.runtime_dashboard_title)
        bindViews()
        bindActions()
        observeRuntimeUiState()
        renderSnapshot()
        Log.d("GHALBIT-DASHBOARD", "dashboard opened")
    }

    override fun onResume() {
        super.onResume()
        runtimeLoadingOverlay.onHostResume()
        runtimeSoftBanner.onHostResume()
        handler.removeCallbacks(refreshRunnable)
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshRunnable)
        runtimeLoadingOverlay.onHostPause()
        runtimeSoftBanner.onHostPause()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshRunnable)
        runtimeLoadingOverlay.onHostDestroy()
        runtimeSoftBanner.onHostDestroy()
        super.onDestroy()
    }

    private fun bindViews() {
        txtRuntimeStatus = findViewById(R.id.txtRuntimeStatus)
        txtMeshStatus = findViewById(R.id.txtMeshStatus)
        txtContactStatus = findViewById(R.id.txtContactStatus)
        txtRouteStatus = findViewById(R.id.txtRouteStatus)
        txtPeerStatus = findViewById(R.id.txtPeerStatus)
        txtSosStatus = findViewById(R.id.txtSosStatus)
        txtCallStatus = findViewById(R.id.txtCallStatus)
        txtTraceStatus = findViewById(R.id.txtTraceStatus)
        txtErrorStatus = findViewById(R.id.txtErrorStatus)
    }

    private fun bindActions() {
        findViewById<Button>(R.id.btnRefreshSnapshot).setOnClickListener {
            renderSnapshot()
            Toast.makeText(this, R.string.runtime_dashboard_refreshed, Toast.LENGTH_SHORT).show()
            Log.d("GHALBIT-DASHBOARD-UI", "refresh tapped")
        }
        findViewById<Button>(R.id.btnRunRuntimeVerification).setOnClickListener {
            com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager.logRuntimeVerification("dashboardButton")
            renderSnapshot()
            Toast.makeText(this, R.string.runtime_dashboard_verification_done, Toast.LENGTH_SHORT).show()
            Log.d("GHALBIT-DASHBOARD-UI", "runtime verification tapped")
        }
        findViewById<Button>(R.id.btnTestCallReadiness).setOnClickListener {
            val readiness = VoipReadinessChecker.check(this)
            val dryRun = VoipReadinessChecker.dryRun(this)
            renderSnapshot()
            Toast.makeText(
                this,
                readiness.failureReason ?: getString(R.string.runtime_dashboard_call_ready_ok),
                Toast.LENGTH_SHORT
            ).show()
            Log.d("GHALBIT-DASHBOARD-UI", "call readiness tapped dryrun=${dryRun.chosenRoute}")
        }
        findViewById<Button>(R.id.btnTestChatDelivery).setOnClickListener {
            val report = ChatDeliveryManager.dryRun(this)
            renderSnapshot()
            Toast.makeText(this, report.summary, Toast.LENGTH_SHORT).show()
            Log.d("GHALBIT-DASHBOARD-UI", "chat delivery dryrun tapped")
        }
        findViewById<Button>(R.id.btnOpenSosInbox).setOnClickListener {
            startActivity(Intent(this, SosInboxActivity::class.java))
            Log.d("GHALBIT-DASHBOARD-UI", "open sos inbox")
        }
        findViewById<Button>(R.id.btnOpenContactList).setOnClickListener {
            startActivity(Intent(this, ContactListActivity::class.java))
            Log.d("GHALBIT-DASHBOARD-UI", "open contact list")
        }
        findViewById<Button>(R.id.btnCopyRuntimeSummary).setOnClickListener {
            val summary = buildSummary(RuntimeDashboardProvider.snapshot(this))
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Runtime Summary", summary))
            Toast.makeText(this, R.string.runtime_dashboard_summary_copied, Toast.LENGTH_SHORT).show()
            Log.d("GHALBIT-DASHBOARD-UI", "summary copied")
        }
        findViewById<Button>(R.id.btnClearReadSos).setOnClickListener {
            val removed = SosAlertManager.clearReadItems(this)
            renderSnapshot()
            Toast.makeText(this, getString(R.string.runtime_dashboard_cleared_sos, removed), Toast.LENGTH_SHORT).show()
            Log.d("GHALBIT-DASHBOARD-UI", "cleared read sos count=$removed")
        }
        findViewById<Button>(R.id.btnAudioTruthLab).setOnClickListener {
            lifecycleScope.launch {
                val report = withContext(Dispatchers.IO) { AudioTruthProbe.run(this@RuntimeDashboardActivity) }
                val markdown = AudioReportGenerator.generateMarkdown(report)
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Audio Truth Report", markdown))
                Toast.makeText(
                    this@RuntimeDashboardActivity,
                    "Audio Truth Lab selesai. Health ${report.healthScore}/100 (report disalin).",
                    Toast.LENGTH_LONG
                ).show()
                Log.d("GHALBIT-DASHBOARD-UI", "audio truth lab finished health=${report.healthScore}")
            }
        }
        findViewById<Button>(R.id.btnRunFullDiagnostic).setOnClickListener {
            startActivity(Intent(this, AutoDiagnosticActivity::class.java))
            Log.d("GHALBIT-DASHBOARD-UI", "open auto diagnostic center")
        }
        findViewById<Button>(R.id.btnRunFullDiagnostic).setOnLongClickListener {
            Log.i("GHALBIT-VIRTUAL-CALL", "TRIGGER_RECEIVED source=RuntimeDashboardActivity")
            val intent = Intent(this, AutoDiagnosticActivity::class.java).apply {
                action = OneDeviceIncomingCallDiagnostic.ACTION_RUN_VIRTUAL_CALL_CHECK
            }
            startActivity(intent)
            Toast.makeText(this, "Virtual Incoming Call Check dijalankan.", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun renderSnapshot() {
        renderJob?.cancel()
        renderJob = lifecycleScope.launch {
            val snapshot = withContext(Dispatchers.IO) { RuntimeDashboardProvider.snapshot(this@RuntimeDashboardActivity) }
            txtRuntimeStatus.text =
            "running=${snapshot.isRunning}\nstatus=${snapshot.runtimeStatus}\nstartedAt=${snapshot.startedAt}\nuptime=${snapshot.uptimeLabel}\nrestart=${snapshot.lastRestartReason}\nlocalNode=${snapshot.localNodeId}\nglobalId=${snapshot.localGlobalId}\npublicKeyHash=${snapshot.localPublicKeyHash}\ntransports=${snapshot.activeTransports.joinToString()}"
            txtMeshStatus.text =
            "alive=${snapshot.heartbeatAliveNodes}\nudp=${snapshot.udpListenerStatus}\nsocket=${snapshot.socketServerStatus}\nnearby=${snapshot.nearbyStatus}\nwifiDirect=${snapshot.wifiDirectStatus}"
            txtContactStatus.text =
            "total=${snapshot.totalContacts}\nlive=${snapshot.liveContacts}\noffline=${snapshot.offlineContacts}\nsaved=${snapshot.savedContacts}\nprovisional=${snapshot.provisionalPeers}\npendingQueue=${snapshot.pendingQueueCount}"
            txtRouteStatus.text =
            "knownRoutes=${snapshot.knownRoutes}\ntransport=${snapshot.currentTransport}\nlastUpdate=${snapshot.lastRouteUpdate}\nbest=${snapshot.bestRouteHints.joinToString(" | ").ifBlank { "-" }}\nactive=${snapshot.activeRoutes.joinToString(" | ").ifBlank { "-" }}\nswitch=${snapshot.routeSwitchHistory.joinToString(" | ").ifBlank { "-" }}"
            txtPeerStatus.text =
            "verification=${snapshot.peerVerificationSummary}\nvalidation=${snapshot.validationSummary.joinToString(" | ")}"
            txtSosStatus.text =
            "total=${snapshot.totalSosAlerts}\nunread=${snapshot.unreadSosAlerts}\nlastSource=${snapshot.lastSosSource}\nlastTime=${snapshot.lastSosTime}"
            txtCallStatus.text =
            "state=${snapshot.callState}\npeer=${snapshot.remotePeer}\naudio=${snapshot.audioEngineStatus}\ntx=${snapshot.audioTxCount} rx=${snapshot.audioRxCount}\nkeepalive=${snapshot.keepAliveHealth}\nreadiness=${snapshot.voipReadinessSummary}\ndryrun=${snapshot.voipDryRunSummary}\nchat=${snapshot.chatDeliverySummary}\npending=${snapshot.chatPendingSummary}"
            txtTraceStatus.text =
            snapshot.recentPacketTrace.joinToString("\n").ifBlank { "Belum ada packet trace" }
            txtErrorStatus.text =
            "warning=${snapshot.lastWarning}\nerror=${snapshot.lastError}\npacket=${snapshot.lastPacket}"
            GhalbitTheme.logCardRendered("runtime-dashboard")
            Log.d("GHALBIT-UI-PERF", "skipped heavy animation")
            Log.d("GHALBIT-DASHBOARD-UI", "render alive=${snapshot.heartbeatAliveNodes} contacts=${snapshot.liveContacts}")
        }
    }

    private fun buildSummary(snapshot: RuntimeDashboardSnapshot): String {
        return buildString {
            appendLine("Runtime: ${snapshot.isRunning} / ${snapshot.runtimeStatus}")
            appendLine("Local: ${snapshot.localNodeId} / ${snapshot.localGlobalId}")
            appendLine("Key Hash: ${snapshot.localPublicKeyHash}")
            appendLine("Uptime: ${snapshot.uptimeLabel}")
            appendLine("Alive nodes: ${snapshot.heartbeatAliveNodes}")
            appendLine("Contacts live: ${snapshot.liveContacts}/${snapshot.totalContacts}")
            appendLine("Routes: ${snapshot.knownRoutes}")
            appendLine("Transport: ${snapshot.currentTransport}")
            appendLine("Peer verification: ${snapshot.peerVerificationSummary}")
            appendLine("SOS unread: ${snapshot.unreadSosAlerts}")
            appendLine("Call: ${snapshot.callState} -> ${snapshot.remotePeer}")
            appendLine("Error: ${snapshot.lastError}")
        }
    }

    private fun observeRuntimeUiState() {
        lifecycleScope.launch {
            RuntimeUiStateManager.stateFlow.collectLatest { snapshot ->
                runtimeLoadingOverlay.render(snapshot)
                runtimeSoftBanner.render(snapshot)
                Log.d("GHALBIT-UX", "dashboard state=${snapshot.state}")
            }
        }
    }
}
