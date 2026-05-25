package com.ghalbitnet.meshx2.monitor

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.core.network.ConnectivityStatusDetector
import com.ghalbitnet.meshx2.core.network.HybridConnectivityPlanner
import com.ghalbitnet.meshx2.core.network.InternetGatewayRegistry
import com.ghalbitnet.meshx2.core.network.LatencyEngine
import com.ghalbitnet.meshx2.core.network.TransportPreference
import com.ghalbitnet.meshx2.core.node.NodeStatusManager
import com.ghalbitnet.meshx2.discovery.DiscoveryManager
import com.ghalbitnet.meshx2.model.MeshNode
import com.ghalbitnet.meshx2.routing.MeshRegistry
import com.ghalbitnet.meshx2.routing.RouteDiscovery
import com.ghalbitnet.meshx2.routing.RouteTable
import com.ghalbitnet.meshx2.security.CryptoEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NetworkActivity : AppCompatActivity() {

    companion object {
        private const val AUTO_REFRESH_MS = 8000L
    }

    private lateinit var txtNetworkSummary: TextView
    private lateinit var txtNetworkNodes: TextView
    private lateinit var btnRefreshNetwork: Button
    private lateinit var networkScroll: NestedScrollView
    private var refreshJob: Job? = null
    private var autoRefreshJob: Job? = null
    private var lastSummaryText: String = ""
    private var lastNodesText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_network)

        txtNetworkSummary =
            findViewById(R.id.txtNetworkSummary)

        txtNetworkNodes =
            findViewById(R.id.txtNetworkNodes)

        btnRefreshNetwork =
            findViewById(R.id.btnRefreshNetwork)

        networkScroll =
            findViewById(R.id.networkScroll)

        btnRefreshNetwork.setOnClickListener {
            refreshNetworkWithLatency()
        }

        refreshNetworkWithLatency()
    }

    override fun onResume() {
        super.onResume()
        refreshNetworkWithLatency()
        startAutoRefresh()
    }

    override fun onPause() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
        refreshJob?.cancel()
        refreshJob = null
        btnRefreshNetwork.isEnabled = true
        super.onPause()
    }

    private fun refreshNetworkWithLatency() {
        refreshJob?.cancel()
        btnRefreshNetwork.isEnabled = false

        val loadingSummary =
            if (lastSummaryText.isBlank()) {
                "Refreshing network..."
            } else {
                lastSummaryText + "\nRefreshing network..."
            }

        txtNetworkSummary.text = loadingSummary

        refreshJob = lifecycleScope.launch {
            delay(150)
            val merged =
                withContext(Dispatchers.IO) {
                    loadNodesWithLatency()
                }

            renderNetwork(merged)
            btnRefreshNetwork.isEnabled = true
        }
    }

    private fun loadNodesWithLatency(): List<MeshNode> {
        val discoveryNodes =
            DiscoveryManager.discoverNodes()

        val statusNodes =
            NodeStatusManager.getOnlineNodes()

        val registryNodes =
            MeshRegistry.getNodes()

        val merged =
            TransportPreference.sortNodes(
                (statusNodes + discoveryNodes + registryNodes)
                    .distinctBy { it.name.ifBlank { it.ipAddress } }
            )

        return merged.map { node ->
            val latency =
                LatencyEngine.calculateLatency(node.ipAddress)

            if (latency >= 0) {
                RouteDiscovery.rememberDirectRoute(
                    destinationPeerId = node.name,
                    destinationIp = node.ipAddress,
                    latencyMs = latency.toLong(),
                    trustScore = node.trusted
                )
            }

            node.copy(
                latency = latency,
                online = node.online && latency >= 0
            )
        }
    }

    private fun renderNetwork(
        merged: List<MeshNode>
    ) {
        val onlineCount =
            merged.count { it.online }
        val primaryNode =
            merged.firstOrNull { it.online }
        val connectionSnapshot =
            ConnectivityStatusDetector.snapshot(this, merged)
        val hybridSnapshot =
            HybridConnectivityPlanner.snapshot(this, merged)
        val gatewayCount =
            merged.count { it.online && it.gateway } +
                if (connectionSnapshot.hasInternet) 1 else 0
        val relayCount =
            merged.count { it.online && it.relay }
        val activeGateway =
            InternetGatewayRegistry.summaryText(this, merged)

        val summaryText =
            buildString {
                appendLine("Koneksi    : ${connectionSnapshot.title(this@NetworkActivity)}")
                appendLine("Detail     : ${connectionSnapshot.description(this@NetworkActivity)}")
                appendLine("Hybrid     : ${hybridSnapshot.title}")
                appendLine("Total nodes : ${merged.size}")
                appendLine("Online      : $onlineCount")
                appendLine("Offline     : ${merged.size - onlineCount}")
                appendLine("Gateway     : $gatewayCount")
                appendLine("Relay       : $relayCount")
                appendLine("Gateway Aktif: $activeGateway")
                appendLine("Primary     : ${primaryModeLabel(primaryNode)}")
                appendLine("Updated     : ${formatTime(System.currentTimeMillis())}")
            }

        val nodesText =
            if (merged.isEmpty()) {
                getString(R.string.no_node_online)
            } else {
                buildString {
                    merged.forEachIndexed { index, node ->
                        appendLine("${index + 1}. ${node.name}")
                        appendLine("   Mode     : ${TransportPreference.modeForAddress(node.ipAddress).label}")
                        appendLine("   IP       : ${node.ipAddress}")
                        appendLine("   Peran    : ${ConnectivityStatusDetector.roleLabel(this@NetworkActivity, node.gateway, node.relay)}")
                        appendLine("   Status   : ${if (node.online) "ONLINE" else "OFFLINE"}")
                        appendLine("   Signal   : ${node.signal}")
                        appendLine("   Latency  : ${formatLatency(node.latency)}")
                        appendLine("   Trust    : ${node.trusted}")
                        appendLine("   Key      : ${CryptoEngine.fingerprint(node.publicKey)}")
                        appendLine("   LastSeen : ${formatTime(node.lastSeen)}")
                        appendLine()
                    }

                    appendLine(RouteTable.report())
                }
            }

        if (summaryText != lastSummaryText) {
            txtNetworkSummary.text = summaryText
            lastSummaryText = summaryText
        }

        if (nodesText != lastNodesText) {
            txtNetworkNodes.text = nodesText
            lastNodesText = nodesText
            networkScroll.post {
                networkScroll.scrollTo(0, 0)
            }
        }
    }

    private fun formatLatency(
        latency: Int
    ): String {
        return if (latency >= 0) {
            "$latency ms"
        } else {
            "unreachable"
        }
    }

    private fun formatTime(timestamp: Long): String {
        return SimpleDateFormat(
            "HH:mm:ss",
            Locale.getDefault()
        ).format(Date(timestamp))
    }

    private fun primaryModeLabel(node: MeshNode?): String {
        if (node == null) {
            return "Menunggu LAN / Hotspot"
        }

        return TransportPreference
            .modeForAddress(node.ipAddress)
            .label
    }

    private fun startAutoRefresh() {
        if (autoRefreshJob?.isActive == true) {
            return
        }

        autoRefreshJob =
            lifecycleScope.launch {
                while (true) {
                    delay(AUTO_REFRESH_MS)
                    refreshNetworkWithLatency()
                }
            }
    }
}
