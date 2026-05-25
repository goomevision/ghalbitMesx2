package com.ghalbitnet.meshx2.chat

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.core.node.NodeStatusManager
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.core.server.FirebaseRemoteSyncManager
import com.ghalbitnet.meshx2.economy.InternetBridgePeerPolicyManager
import com.ghalbitnet.meshx2.economy.InternetBridgePolicyManager
import com.ghalbitnet.meshx2.economy.InternetBridgeRequestLogManager
import com.ghalbitnet.meshx2.economy.InternetBridgeRequestQueueManager
import com.ghalbitnet.meshx2.economy.AutoNodeRoleManager
import com.ghalbitnet.meshx2.economy.MeshServiceLedger
import com.ghalbitnet.meshx2.economy.PeerReputationManager
import com.ghalbitnet.meshx2.security.KeyStoreManager
import com.ghalbitnet.meshx2.token.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RemoteContactInfoActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_GLOBAL_ID = "global_id"
        private const val WALLET_QR_PREFIX = "GHBT:"

        fun createIntent(
            context: Context,
            globalId: String
        ): Intent {
            return Intent(context, RemoteContactInfoActivity::class.java).apply {
                putExtra(EXTRA_GLOBAL_ID, globalId)
            }
        }
    }

    private lateinit var globalId: String
    private lateinit var ownerGlobalId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote_contact_info)

        globalId = intent.getStringExtra(EXTRA_GLOBAL_ID).orEmpty()
        ownerGlobalId =
            GlobalMeshIdentityManager.buildGlobalId(
                KeyStoreManager(this).publicKeyBase64
            )
        TokenManager.init(this)

        findViewById<Button>(R.id.btnRemoteContactPriority).setOnClickListener {
            val next =
                !GlobalContactDirectory.isPrioritized(this, globalId)
            GlobalContactDirectory.setPrioritized(this, globalId, next)
            Toast.makeText(
                this,
                if (next) {
                    getString(R.string.remote_contact_priority_enabled)
                } else {
                    getString(R.string.remote_contact_priority_disabled)
                },
                Toast.LENGTH_SHORT
            ).show()
            bindState()
        }

        findViewById<Button>(R.id.btnRemoteContactEdit).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnRemoteContactTopUp).setOnClickListener {
            showTopUpDialog()
        }

        findViewById<Button>(R.id.btnRemoteContactWalletQr).setOnClickListener {
            showContactWalletQr()
        }

        refreshRemoteControlState()
    }

    override fun onResume() {
        super.onResume()
        refreshRemoteControlState()
    }

    private fun refreshRemoteControlState() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                FirebaseRemoteSyncManager.refreshControlData(
                    this@RemoteContactInfoActivity,
                    setOf(ownerGlobalId, globalId)
                )
            }
            bindState()
        }
    }

    private fun bindState() {
        InternetBridgeRequestQueueManager.reevaluate(this)

        val contact =
            GlobalContactDirectory.find(this, globalId) ?: return
        val nodes =
            NodeStatusManager.getOnlineNodes()
        val state =
            RemotePresenceRegistry.contactState(
                context = this,
                nodes = nodes,
                globalId = contact.globalId
            )
        val presence =
            RemotePresenceRegistry.snapshot(this)
        val prioritized =
            GlobalContactDirectory.isPrioritized(this, globalId)
        val bridgePolicy =
            InternetBridgePeerPolicyManager.getPolicy(this, globalId)
        val bridgeDecision =
            InternetBridgePolicyManager.evaluate(this, globalId)
        val bridgeDecisionSummary =
            InternetBridgeRequestLogManager.decisionSummaryForPeer(this, globalId)
        val bridgeQueue =
            InternetBridgeRequestQueueManager.currentForPeer(this, globalId)
        val bridgeQueuePosition =
            InternetBridgeRequestQueueManager.queuePositionForPeer(this, globalId)
        val peerEconomy =
            MeshServiceLedger.peerSnapshot(this, contact.globalId)
        val reputation =
            PeerReputationManager.calculate(peerEconomy, bridgeDecisionSummary)
        val autoRole =
            AutoNodeRoleManager.peer(
                context = this,
                economySnapshot = peerEconomy,
                reputation = reputation,
                routeAllowed = bridgeDecision.allowed
            )
        val walletBalances =
            kotlinx.coroutines.runBlocking {
                withContext(Dispatchers.IO) {
                    TokenManager.ensureWalletBootstrap(ownerGlobalId)
                    TokenManager.ensureWalletBootstrap(contact.globalId)
                    val ownerLocal = TokenManager.getWalletBalanceForGlobalId(ownerGlobalId)
                    val contactLocal = TokenManager.getWalletBalanceForGlobalId(contact.globalId)
                    Triple(
                        FirebaseRemoteSyncManager.cachedWalletBalance(this@RemoteContactInfoActivity, ownerGlobalId)
                            ?: ownerLocal,
                        FirebaseRemoteSyncManager.cachedWalletBalance(this@RemoteContactInfoActivity, contact.globalId)
                            ?: contactLocal,
                        TokenManager.getWalletTransactions(contact.globalId, 5)
                    )
                }
            }
        val ownerWalletBalance = walletBalances.first
        val contactWalletBalance = walletBalances.second
        val contactWalletTransactions = walletBalances.third

        findViewById<TextView>(R.id.txtRemoteContactTitle).text = contact.alias
        findViewById<TextView>(R.id.txtRemoteContactId).text =
            getString(R.string.saved_contacts_global_id_value, contact.globalId)
        findViewById<TextView>(R.id.txtRemoteContactGroup).text =
            contact.group.ifBlank { getString(R.string.saved_contacts_group_none) }
        findViewById<TextView>(R.id.txtRemoteContactNote).text =
            contact.note.ifBlank { getString(R.string.remote_contact_note_empty) }
        findViewById<TextView>(R.id.txtRemoteContactStatus).text = state.label
        findViewById<TextView>(R.id.txtRemoteContactDetail).text = state.detail
        findViewById<TextView>(R.id.txtRemoteContactSync).text =
            presence.lastSyncLabel(this)
        findViewById<TextView>(R.id.txtRemoteContactPriorityState).text =
            if (prioritized) {
                getString(R.string.remote_contact_priority_yes)
            } else {
                getString(R.string.remote_contact_priority_no)
            }
        findViewById<TextView>(R.id.txtRemoteContactReputationState).text =
            getString(
                R.string.remote_contact_reputation_value,
                reputation.score,
                reputation.label,
                reputation.detail
            )
        findViewById<TextView>(R.id.txtRemoteContactAutoRoleState).text =
            getString(
                R.string.auto_role_value,
                autoRole.title,
                autoRole.trustScore,
                autoRole.contributionScore,
                autoRole.detail
            )
        findViewById<TextView>(R.id.txtRemoteContactBridgeTierState).text =
            displayServiceTier(bridgePolicy.tier.name)
        findViewById<TextView>(R.id.txtRemoteContactBridgeQuotaState).text =
            bridgePolicy.customDailyQuotaMb?.let {
                getString(R.string.remote_contact_bridge_quota_value, it)
            } ?: getString(R.string.remote_contact_bridge_quota_default)
        findViewById<TextView>(R.id.txtRemoteContactBridgeDecisionState).text =
            if (bridgeDecision.allowed) {
                getString(R.string.remote_contact_bridge_allowed)
            } else {
                getString(R.string.remote_contact_bridge_denied)
            }
        findViewById<TextView>(R.id.txtRemoteContactBridgeDecisionDetail).text =
            getString(
                R.string.remote_contact_bridge_decision_value,
                displayLaneStatus(bridgeDecision.routeMode.name),
                bridgeDecision.walletBalance,
                bridgeDecision.dailyUsedMb,
                bridgeDecision.dailyQuotaMb,
                bridgeDecision.detail
            )
        findViewById<TextView>(R.id.txtRemoteContactEconomyState).text =
            getString(
                R.string.remote_contact_economy_value,
                peerEconomy.sessionCount,
                peerEconomy.totalMegaBytes,
                peerEconomy.totalBurned,
                peerEconomy.totalGatewayReward,
                peerEconomy.totalRelayReward,
                if (peerEconomy.lastUpdatedAt > 0L) {
                    SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                        .format(Date(peerEconomy.lastUpdatedAt))
                } else {
                    getString(R.string.remote_contact_economy_never)
                }
            )
        findViewById<TextView>(R.id.txtRemoteContactWalletState).text =
            getString(
                R.string.remote_contact_wallet_value,
                contactWalletBalance,
                ownerWalletBalance
            )
        findViewById<TextView>(R.id.txtRemoteContactWalletRecentState).text =
            if (contactWalletTransactions.isEmpty()) {
                getString(R.string.remote_contact_wallet_recent_empty)
            } else {
                val formatter =
                    SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                contactWalletTransactions.joinToString("\n\n") { tx ->
                    buildString {
                        append(formatter.format(Date(tx.timestamp)))
                        append('\n')
                        append(
                            getString(
                                R.string.remote_contact_wallet_recent_value,
                                tx.amount,
                                tx.reason
                            )
                        )
                    }
                }
            }
        findViewById<TextView>(R.id.txtRemoteContactBridgeQueueState).text =
            if (bridgeQueue == null) {
                getString(R.string.remote_contact_bridge_queue_empty)
            } else {
                getString(
                    R.string.remote_contact_bridge_queue_value,
                    displayQueueStatus(bridgeQueue.status.name),
                    displayServiceTier(bridgeQueue.tier.name),
                    bridgeQueue.reputationScore,
                    bridgeQueue.reputationLabel,
                    displayLaneStatus(bridgeQueue.routeMode.name),
                    when (bridgeQueue.status) {
                        InternetBridgeRequestQueueManager.QueueStatus.ACTIVE ->
                            getString(R.string.remote_contact_bridge_queue_active_position)
                        InternetBridgeRequestQueueManager.QueueStatus.WAITING ->
                            getString(
                                R.string.remote_contact_bridge_queue_waiting_position,
                                bridgeQueuePosition ?: 1
                            )
                        InternetBridgeRequestQueueManager.QueueStatus.DENIED ->
                    getString(R.string.remote_contact_bridge_queue_denied_position)
                    },
                    bridgeQueue.detail
                )
            }
        findViewById<Button>(R.id.btnRemoteContactPriority).text =
            if (prioritized) {
                getString(R.string.remote_contact_priority_disable_button)
            } else {
                getString(R.string.remote_contact_priority_enable_button)
            }
    }

    private fun displayServiceTier(
        tier: String
    ): String {
        return when (tier.uppercase(Locale.getDefault())) {
            "PRIORITY" -> getString(R.string.remote_contact_tier_priority)
            "BLOCKED" -> getString(R.string.remote_contact_tier_blocked)
            else -> getString(R.string.remote_contact_tier_standard)
        }
    }

    private fun displayLaneStatus(
        routeMode: String
    ): String {
        return when (routeMode.uppercase(Locale.getDefault())) {
            "LOCAL_DIRECT" -> getString(R.string.remote_contact_lane_direct)
            "REMOTE_GATEWAY" -> getString(R.string.remote_contact_lane_remote)
            else -> getString(R.string.remote_contact_lane_waiting)
        }
    }

    private fun displayQueueStatus(
        status: String
    ): String {
        return when (status.uppercase(Locale.getDefault())) {
            "ACTIVE" -> getString(R.string.remote_contact_queue_active)
            "WAITING" -> getString(R.string.remote_contact_queue_waiting)
            else -> getString(R.string.remote_contact_queue_limited)
        }
    }

    private fun showTopUpDialog() {
        val contact =
            GlobalContactDirectory.find(this, globalId) ?: return

        val input =
            EditText(this).apply {
                hint = getString(R.string.remote_contact_topup_hint)
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.remote_contact_topup_title, contact.alias))
            .setView(input)
            .setPositiveButton(R.string.remote_contact_topup_button) { _, _ ->
                val amount =
                    input.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0

                if (amount <= 0.0) {
                    Toast.makeText(
                        this,
                        getString(R.string.wallet_transfer_invalid),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    val ok =
                        withContext(Dispatchers.IO) {
                            TokenManager.transferBetweenWallets(
                                fromGlobalId = ownerGlobalId,
                                toGlobalId = contact.globalId,
                                amount = amount,
                                reason = "REMOTE_TOP_UP"
                            )
                        }

                    Toast.makeText(
                        this@RemoteContactInfoActivity,
                        if (ok) {
                            getString(
                                R.string.remote_contact_topup_success,
                                amount,
                                contact.alias
                            )
                        } else {
                            getString(R.string.remote_contact_topup_failed)
                        },
                        Toast.LENGTH_SHORT
                    ).show()

                    bindState()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showContactWalletQr() {
        val contact =
            GlobalContactDirectory.find(this, globalId) ?: return
        val qrBitmap =
            buildWalletQrBitmap(contact.globalId)

        if (qrBitmap == null) {
            Toast.makeText(
                this,
                getString(R.string.wallet_scan_invalid),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val container =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 16, 32, 0)
                addView(
                    TextView(this@RemoteContactInfoActivity).apply {
                        text = getString(R.string.remote_contact_wallet_qr_value, contact.alias)
                        setTextColor(Color.parseColor("#E8F1F8"))
                        textSize = 14f
                    }
                )
                addView(
                    ImageView(this@RemoteContactInfoActivity).apply {
                        setImageBitmap(qrBitmap)
                        setBackgroundColor(Color.WHITE)
                        adjustViewBounds = true
                        setPadding(24, 24, 24, 24)
                    }
                )
                addView(
                    TextView(this@RemoteContactInfoActivity).apply {
                        text = contact.globalId
                        setTextColor(Color.parseColor("#FFD54F"))
                        textSize = 14f
                        setPadding(0, 12, 0, 0)
                    }
                )
            }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.remote_contact_wallet_qr_title, contact.alias))
            .setView(container)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun buildWalletQrBitmap(targetGlobalId: String): Bitmap? {
        return try {
            val bitMatrix: BitMatrix =
                MultiFormatWriter().encode(
                    "$WALLET_QR_PREFIX$targetGlobalId",
                    BarcodeFormat.QR_CODE,
                    720,
                    720
                )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    pixels[y * width + x] =
                        if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (_: Exception) {
            null
        }
    }

}
