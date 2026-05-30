package com.ghalbitnet.meshx2.economy

import android.os.Bundle
import android.content.Intent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.core.network.ConnectivityStatusDetector
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.core.network.InternetGatewayRegistry
import com.ghalbitnet.meshx2.discovery.DiscoveryManager
import com.ghalbitnet.meshx2.token.TokenManager
import com.ghalbitnet.meshx2.security.KeyStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    private lateinit var txtEconomyReserve: TextView
    private lateinit var txtEconomyRecent: TextView
    private lateinit var btnEconomySimulate: Button
    private lateinit var btnEconomyClear: Button
    private lateinit var btnEconomyLedger: Button

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
        txtEconomyReserve = findViewById(R.id.txtEconomyReserve)
        txtEconomyRecent = findViewById(R.id.txtEconomyRecent)
        btnEconomySimulate = findViewById(R.id.btnEconomySimulate)
        btnEconomyClear = findViewById(R.id.btnEconomyClear)
        btnEconomyLedger = findViewById(R.id.btnEconomyLedger)

        btnEconomySimulate.setOnClickListener {
            simulateServiceSession()
        }

        btnEconomyClear.setOnClickListener {
            MeshServiceLedger.clear(this)
            Toast.makeText(this, getString(R.string.service_economy_reset_done), Toast.LENGTH_SHORT).show()
            refreshUi()
        }

        btnEconomyLedger.setOnClickListener {
            startActivity(Intent(this, MeshEconomyLedgerActivity::class.java))
        }

        refreshUi()
    }

    private fun simulateServiceSession() {
        btnEconomySimulate.isEnabled = false
        btnEconomySimulate.text = getString(R.string.service_economy_simulate_busy)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                TokenManager.ensureWalletBootstrap(globalId)

                val nodes =
                    DiscoveryManager.discoverNodes()

                val session =
                    buildDemoSession(nodes)

                val settlement =
                    MeshServiceFormula.settle(session)

                applySettlement(session, settlement)

                MeshServiceLedger.record(
                    this@MeshEconomyActivity,
                    ServiceLedgerEntry(session, settlement)
                )

                runOnUiThread {
                    Toast.makeText(
                        this@MeshEconomyActivity,
                        getString(R.string.service_economy_session_saved),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                runOnUiThread {
                    btnEconomySimulate.isEnabled = true
                    btnEconomySimulate.text = getString(R.string.service_economy_simulate)
                    refreshUi()
                }
            }
        }
    }

    private suspend fun applySettlement(
        session: ServiceSessionRecord,
        settlement: ServiceSettlement
    ) {
        // TODO unified identity:
        // reward attribution should migrate from nodeName/nodeAddress fallback
        // to canonical participant identity backed by globalId.
        TokenManager.recordWalletDebit(
            globalId,
            settlement.burnAmount,
            "USAGE_BURN:${session.sessionId}"
        )

        if (session.localInternetProvider) {
            TokenManager.recordWalletCredit(
                globalId,
                settlement.gatewayReward,
                "GATEWAY_REWARD:${session.sessionId}"
            )
        } else if (settlement.gatewayReward > 0.0) {
            TokenManager.recordPeerReward(
                peerIp = session.gatewayNodeAddress.ifBlank { session.gatewayNodeId },
                peerName = session.gatewayNodeName.ifBlank { session.gatewayNodeId },
                amount = settlement.gatewayReward,
                reason = "GATEWAY_REWARD:${session.sessionId}"
            )
        }

        settlement.relayRewards.forEach { relay ->
            if (relay.local) {
                TokenManager.recordWalletCredit(
                    globalId,
                    relay.amount,
                    "RELAY_REWARD:${session.sessionId}:${relay.nodeName}"
                )
            } else {
                TokenManager.recordPeerReward(
                    peerIp = relay.nodeAddress.ifBlank { relay.nodeId },
                    peerName = relay.nodeName,
                    amount = relay.amount,
                    reason = "RELAY_REWARD:${session.sessionId}"
                )
            }
        }

        TokenManager.recordTreasury(
            amount = settlement.treasuryReserve,
            reason = "TREASURY:${session.sessionId}"
        )

        TokenManager.recordBuilderReward(
            amount = settlement.builderReward,
            reason = "BUILDER_REWARD:${session.sessionId}"
        )
    }

    private fun buildDemoSession(
        nodes: List<com.ghalbitnet.meshx2.model.MeshNode>
    ): ServiceSessionRecord {
        val now =
            System.currentTimeMillis()

        val localGateway =
            ConnectivityStatusDetector.localGatewayActive(this)

        val remoteGateway =
            if (localGateway) {
                null
            } else {
                InternetGatewayRegistry.select(this, nodes)
            }

        val relayNodes =
            nodes.filter { it.online && it.relay && it.name != remoteGateway?.name }

        val observedRelayPath =
            ServicePathRecorder.recentRelayParticipants(this, 4)

        val observedUsage =
            UsageSessionRecorder.latestObservedSession(this)

        val serviceFamily =
            observedUsage?.serviceFamily ?: when {
                observedRelayPath.isNotEmpty() || relayNodes.isNotEmpty() -> ServiceFamily.MEDIA
                else -> ServiceFamily.CHAT
            }

        val relayPath =
            if (observedRelayPath.isNotEmpty()) {
                observedRelayPath
            } else {
                relayNodes
                    .sortedByDescending { it.trusted }
                    .take(3)
                    .mapIndexed { index, node ->
                        ServiceParticipant(
                            nodeId = "${node.name}-$index",
                            nodeName = node.name,
                            nodeAddress = node.ipAddress,
                            role = ServiceRole.RELAY,
                            local = false,
                            trustScore = node.trusted.coerceIn(10, 100)
                        )
                    }
            }

        val avgLatency =
            if (relayPath.isEmpty()) 90 else (70 + relayPath.size * 20)

        val observedTotalBytes =
            observedUsage?.totalBytes ?: 0L

        val fallbackTrafficMb =
            when {
                localGateway -> 36
                remoteGateway != null -> 24
                else -> 10
            } + relayPath.size * 4

        val totalTrafficBytes =
            if (observedTotalBytes > 0L) {
                observedTotalBytes
            } else {
                fallbackTrafficMb * 1024L * 1024L
            }

        val durationMs =
            observedUsage?.durationMs
                ?: (120_000L + relayPath.size * 45_000L)

        return ServiceSessionRecord(
            sessionId = "svc-${now}",
            serviceFamily = serviceFamily,
            userGlobalId = globalId,
            bytesUp = totalTrafficBytes / 3L,
            bytesDown = totalTrafficBytes,
            durationMs = durationMs,
            startedAt = now - durationMs,
            endedAt = now,
            success = localGateway || remoteGateway != null,
            averageLatencyMs = avgLatency,
            localInternetProvider = localGateway,
            gatewayNodeId = if (localGateway) globalId else remoteGateway?.name.orEmpty(),
            gatewayNodeName = if (localGateway) getString(R.string.gateway_this_device) else remoteGateway?.name.orEmpty(),
            gatewayNodeAddress = if (localGateway) "local" else remoteGateway?.ipAddress.orEmpty(),
            relayPath = relayPath
        )
    }

    private fun refreshUi() {
        lifecycleScope.launch(Dispatchers.IO) {
            TokenManager.ensureWalletBootstrap(globalId)

            val walletBalance =
                TokenManager.getLocalWalletBalance(globalId)

            val builderBalance =
                TokenManager.getBuilderWalletBalance()

            val snapshot =
                MeshServiceLedger.snapshot(this@MeshEconomyActivity)

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
                    getString(R.string.service_economy_wallet_value, walletBalance)
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
                txtEconomyReserve.text =
                    getString(R.string.service_economy_reserve_value, snapshot.totalTreasury)
                txtEconomyRecent.text = recentText
            }
        }
    }
}
