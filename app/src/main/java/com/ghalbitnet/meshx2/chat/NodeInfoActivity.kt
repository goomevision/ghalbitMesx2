package com.ghalbitnet.meshx2.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.core.network.ConnectivityStatusDetector
import com.ghalbitnet.meshx2.core.network.InternetGatewayRegistry
import com.ghalbitnet.meshx2.core.network.TransportPreference
import com.ghalbitnet.meshx2.core.node.NodeStatusManager

class NodeInfoActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_PEER_NAME = "peer_name"
        private const val EXTRA_PEER_IP = "peer_ip"
        private const val EXTRA_MODE_LABEL = "mode_label"
        private const val EXTRA_QUALITY_LABEL = "quality_label"
        private const val EXTRA_SIGNAL = "signal"
        private const val EXTRA_LATENCY = "latency"
        private const val EXTRA_TRUSTED = "trusted"
        private const val EXTRA_SUMMARY = "summary"
        private const val EXTRA_SUMMARY_TIME = "summary_time"
        private const val EXTRA_UNREAD_COUNT = "unread_count"
        private const val EXTRA_PINNED = "pinned"

        fun createIntent(
            context: Context,
            item: ContactListItem
        ): Intent {
            return Intent(context, NodeInfoActivity::class.java).apply {
                putExtra(EXTRA_PEER_NAME, item.peerName)
                putExtra(EXTRA_PEER_IP, item.peerIp)
                putExtra(EXTRA_MODE_LABEL, item.modeLabel)
                putExtra(EXTRA_QUALITY_LABEL, item.qualityLabel)
                putExtra(EXTRA_SIGNAL, item.signal)
                putExtra(EXTRA_LATENCY, item.latency)
                putExtra(EXTRA_TRUSTED, item.trusted)
                putExtra(EXTRA_SUMMARY, item.summary)
                putExtra(EXTRA_SUMMARY_TIME, item.summaryTime)
                putExtra(EXTRA_UNREAD_COUNT, item.unreadCount)
                putExtra(EXTRA_PINNED, item.pinned)
            }
        }
    }

    private lateinit var peerName: String
    private lateinit var peerIp: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_node_info)

        peerName = intent.getStringExtra(EXTRA_PEER_NAME).orEmpty()
        peerIp = intent.getStringExtra(EXTRA_PEER_IP).orEmpty()
        bindNodeInfo()

        findViewById<Button>(R.id.btnNodeOpenChat).setOnClickListener {
            startActivity(
                Intent(this, ChatActivity::class.java).apply {
                    putExtra("peerIp", peerIp)
                    putExtra("peerName", peerName)
                }
            )
        }

        findViewById<Button>(R.id.btnNodeTogglePin).setOnClickListener {
            val nextPinned =
                ContactPinManager.togglePinned(this, peerName)
            Toast.makeText(
                this,
                if (nextPinned) {
                    getString(R.string.contact_pin_added, peerName)
                } else {
                    getString(R.string.contact_pin_removed, peerName)
                },
                Toast.LENGTH_SHORT
            ).show()
            bindNodeInfo()
        }

        findViewById<Button>(R.id.btnNodeSaveContact).setOnClickListener {
            showSaveContactDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        bindNodeInfo()
    }

    private fun bindNodeInfo() {
        val latestNode =
            NodeStatusManager.findNode(peerName)
        val modeLabel =
            latestNode?.let {
                TransportPreference.modeForAddress(it.ipAddress).label
            } ?: intent.getStringExtra(EXTRA_MODE_LABEL).orEmpty()
        val qualityLabel = intent.getStringExtra(EXTRA_QUALITY_LABEL).orEmpty()
        val signal = latestNode?.signal ?: intent.getIntExtra(EXTRA_SIGNAL, 0)
        val latency = latestNode?.latency ?: intent.getIntExtra(EXTRA_LATENCY, 0)
        val trusted = latestNode?.trusted ?: intent.getIntExtra(EXTRA_TRUSTED, 0)
        val summary = intent.getStringExtra(EXTRA_SUMMARY).orEmpty()
        val summaryTime = intent.getStringExtra(EXTRA_SUMMARY_TIME).orEmpty()
        val unreadCount = intent.getIntExtra(EXTRA_UNREAD_COUNT, 0)
        val pinned = ContactPinManager.isPinned(this, peerName)
        val displayName = ContactAliasManager.getDisplayName(this, peerName)
        val lastSeen = NodeStatusManager.getLastSeen(peerName)
        val online = NodeStatusManager.isOnline(peerName)
        val activityLines =
            NodeStatusManager.getRecentActivity(peerName)
        val activeGateway =
            InternetGatewayRegistry.select(
                this,
                NodeStatusManager.getOnlineNodes()
            )
        val roleLabel =
            ConnectivityStatusDetector.roleLabel(
                this,
                latestNode?.gateway == true,
                latestNode?.relay != false
            )
        val gatewayBadge =
            if (activeGateway?.isLocal == false && activeGateway.name == peerName) {
                getString(R.string.gateway_active_node_short)
            } else {
                ""
            }

        findViewById<TextView>(R.id.txtNodeTitle).text = displayName
        findViewById<TextView>(R.id.txtNodeIp).text =
            if (displayName != peerName) {
                getString(R.string.node_info_identity_value, peerName, peerIp)
            } else {
                peerIp
            }
        findViewById<TextView>(R.id.txtNodeMode).text =
            listOf(
                modeLabel.ifBlank { getString(R.string.transport_other_fallback) },
                roleLabel
            ).joinToString(" • ")
        findViewById<TextView>(R.id.txtNodeQuality).text = qualityLabel
            .let {
                if (gatewayBadge.isBlank()) {
                    it
                } else {
                    "$it • $gatewayBadge"
                }
            }
        findViewById<TextView>(R.id.txtNodeSignal).text =
            getString(R.string.node_info_signal_value, signal.coerceIn(0, 100))
        findViewById<TextView>(R.id.txtNodeLatency).text =
            getString(R.string.node_info_latency_value, latency.coerceAtLeast(0))
        findViewById<TextView>(R.id.txtNodeTrusted).text =
            getString(R.string.node_info_trust_value, trusted.coerceIn(0, 100))
        findViewById<ProgressBar>(R.id.progressSignal).progress =
            signal.coerceIn(0, 100)
        findViewById<ProgressBar>(R.id.progressLatency).progress =
            (100 - latency.coerceIn(0, 100))
        findViewById<ProgressBar>(R.id.progressTrusted).progress =
            trusted.coerceIn(0, 100)
        findViewById<TextView>(R.id.txtNodeSummary).text = summary
        findViewById<TextView>(R.id.txtNodeSummaryTime).text = summaryTime
        findViewById<TextView>(R.id.txtNodeUnread).text = unreadCount.toString()
        findViewById<TextView>(R.id.txtNodePinned).text =
            if (pinned) getString(R.string.node_info_pinned_yes) else getString(R.string.node_info_pinned_no)
        findViewById<Button>(R.id.btnNodeSaveContact).text =
            if (ContactAliasManager.hasAlias(this, peerName)) {
                getString(R.string.contact_action_edit_contact)
            } else {
                getString(R.string.contact_action_save_contact)
            }
        findViewById<TextView>(R.id.txtNodeCurrentStatus).text =
            if (online) {
                getString(R.string.node_info_status_online)
            } else {
                getString(R.string.node_info_status_offline)
            }
        findViewById<TextView>(R.id.txtNodeLastSeen).text =
            if (lastSeen > 0L) {
                DateUtils.getRelativeTimeSpanString(
                    lastSeen,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                ).toString()
            } else {
                getString(R.string.node_info_last_seen_empty)
            }
        findViewById<TextView>(R.id.txtNodeActivity).text =
            if (activityLines.isEmpty()) {
                getString(R.string.node_info_activity_empty)
            } else {
                activityLines.joinToString("\n") { record ->
                    val relativeTime =
                        DateUtils.getRelativeTimeSpanString(
                            record.timestamp,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS
                        )
                    "\u2022 $relativeTime - ${record.summary}"
                }
            }
    }

    private fun showSaveContactDialog() {
        val input =
            EditText(this).apply {
                setText(ContactAliasManager.getAlias(this@NodeInfoActivity, peerName).orEmpty())
                hint = getString(R.string.contact_alias_hint)
                setSelection(text.length)
            }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.contact_save_dialog_title))
            .setView(input)
            .setPositiveButton(R.string.contact_save_button) { _, _ ->
                ContactAliasManager.saveAlias(
                    this,
                    peerName,
                    input.text?.toString().orEmpty()
                )
                Toast.makeText(
                    this,
                    getString(
                        R.string.contact_saved_message,
                        ContactAliasManager.getDisplayName(this, peerName)
                    ),
                    Toast.LENGTH_SHORT
                ).show()
                bindNodeInfo()
            }
            .setNeutralButton(R.string.contact_remove_button) { _, _ ->
                ContactAliasManager.removeAlias(this, peerName)
                Toast.makeText(
                    this,
                    getString(R.string.contact_removed_message, peerName),
                    Toast.LENGTH_SHORT
                ).show()
                bindNodeInfo()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
