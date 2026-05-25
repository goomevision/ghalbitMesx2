package com.ghalbitnet.meshx2.chat

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.core.network.HybridConnectivityPlanner
import com.ghalbitnet.meshx2.core.network.InternetGatewayRegistry
import com.ghalbitnet.meshx2.core.node.NodeStatusManager
import com.ghalbitnet.meshx2.core.server.FirebaseBootstrapHandshakeManager
import com.ghalbitnet.meshx2.core.server.FirebaseBootstrapPeerManager
import kotlinx.coroutines.launch

class RemoteModeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote_mode)

        findViewById<Button>(R.id.btnRemoteToggle).setOnClickListener {
            RemoteModeManager.toggle(this)
            lifecycleScope.launch {
                RemotePresenceRegistry.refresh(this@RemoteModeActivity, NodeStatusManager.getOnlineNodes())
                bindState()
            }
        }

        findViewById<Button>(R.id.btnRemoteRefresh).setOnClickListener {
            lifecycleScope.launch {
                val snapshot =
                    RemotePresenceRegistry.refresh(this@RemoteModeActivity, NodeStatusManager.getOnlineNodes())
                Toast.makeText(
                    this@RemoteModeActivity,
                    snapshot.lastSyncLabel(this@RemoteModeActivity),
                    Toast.LENGTH_SHORT
                ).show()
                bindState()
            }
        }

        findViewById<Button>(R.id.btnRemoteOpenContacts).setOnClickListener {
            startActivity(Intent(this, SavedContactsActivity::class.java))
        }
        bindState()
    }

    override fun onResume() {
        super.onResume()
        bindState()
    }

    private fun bindState() {
        val nodes =
            NodeStatusManager.getOnlineNodes()
        val hybrid =
            HybridConnectivityPlanner.snapshot(this, nodes)
        val gatewaySummary =
            InternetGatewayRegistry.summaryText(this, nodes)
        val remoteContacts =
            GlobalContactDirectory.getAll(this)
        val remoteMode =
            RemoteModeManager.snapshot(this, nodes)
        val presence =
            RemotePresenceRegistry.snapshot(this)
        val bootstrapPeers =
            FirebaseBootstrapPeerManager.cachedPeers(this)
        val bootstrapHandshake =
            FirebaseBootstrapHandshakeManager.snapshot(this)
        findViewById<TextView>(R.id.txtRemoteStatus).text = hybrid.title
        findViewById<TextView>(R.id.txtRemoteSummary).text = hybrid.description
        findViewById<TextView>(R.id.txtRemoteGateway).text = gatewaySummary
        findViewById<TextView>(R.id.txtRemoteModeState).text =
            if (remoteMode.enabled) {
                getString(R.string.remote_mode_enabled)
            } else {
                getString(R.string.remote_mode_disabled)
            }
        findViewById<TextView>(R.id.txtRemoteContacts).text =
            resources.getQuantityString(
                R.plurals.remote_contacts_count,
                remoteContacts.size,
                remoteContacts.size
            )
        findViewById<TextView>(R.id.txtRemotePresenceSync).text =
            presence.lastSyncLabel(this)
        findViewById<TextView>(R.id.txtRemotePresenceTracked).text =
            resources.getQuantityString(
                R.plurals.remote_presence_tracked_count,
                presence.trackedCount,
                presence.trackedCount
            )
        findViewById<TextView>(R.id.txtRemotePresenceOnline).text =
            resources.getQuantityString(
                R.plurals.remote_presence_online_count,
                presence.onlineCount,
                presence.onlineCount
            )

        findViewById<TextView>(R.id.txtRemoteServerTitle).text = remoteMode.title
        findViewById<TextView>(R.id.txtRemoteServer).text = remoteMode.description
        findViewById<Button>(R.id.btnRemoteToggle).text =
            if (remoteMode.enabled) {
                getString(R.string.remote_mode_disable_button)
            } else {
                getString(R.string.remote_mode_enable_button)
            }

        findViewById<TextView>(R.id.txtRemoteSteps).text =
            buildString {
                appendLine(getString(R.string.remote_step_identity))
                appendLine(getString(R.string.remote_step_contacts))
                appendLine(getString(R.string.remote_step_gateway))
                if (bootstrapPeers.isNotEmpty()) {
                    appendLine(
                        getString(
                            R.string.remote_step_bootstrap_ready,
                            bootstrapPeers.size,
                            bootstrapPeers.first().alias
                        )
                    )
                    appendLine(
                        getString(
                            R.string.remote_step_bootstrap_handshake,
                            bootstrapHandshake.ackedCount,
                            bootstrapHandshake.pendingCount,
                            bootstrapHandshake.detail
                        )
                    )
                }
                append(
                    if (remoteMode.enabled) {
                        getString(R.string.remote_step_server_enabled)
                    } else {
                        getString(R.string.remote_step_server)
                    }
                )
            }
    }
}
