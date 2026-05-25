package com.ghalbitnet.meshx2.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.access.ClientIdentityMatcher
import com.ghalbitnet.meshx2.access.ClientTrustLevel
import com.ghalbitnet.meshx2.access.HotspotBlocklistAssistant
import com.ghalbitnet.meshx2.access.HotspotNetworkScanner
import com.ghalbitnet.meshx2.access.NetworkAccessPolicy
import com.ghalbitnet.meshx2.access.ProviderClientActionManager
import com.ghalbitnet.meshx2.access.UnauthorizedClientUiModel
import com.ghalbitnet.meshx2.vpn.VpnLogManager
import kotlinx.coroutines.launch

class UnauthorizedClientsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPENED_FROM_NOTIFICATION = "opened_from_notification"
    }

    private lateinit var txtSummary: TextView
    private lateinit var txtHint: TextView
    private lateinit var txtEmpty: TextView
    private lateinit var btnOpenSettings: Button
    private lateinit var btnScanNow: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UnauthorizedClientCardAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_unauthorized_clients)

        txtSummary = findViewById(R.id.txtUnauthorizedClientsSummary)
        txtHint = findViewById(R.id.txtUnauthorizedClientsHint)
        txtEmpty = findViewById(R.id.txtUnauthorizedClientsEmpty)
        btnOpenSettings = findViewById(R.id.btnUnauthorizedClientsOpenSettings)
        btnScanNow = findViewById(R.id.btnUnauthorizedClientsScanNow)
        recyclerView = findViewById(R.id.rvUnauthorizedClients)

        adapter =
            UnauthorizedClientCardAdapter(
                onCopyMac = ::copyMac,
                onCopyIp = ::copyIp,
                onBlock = ::blockClient,
                onAllow = ::allowClient,
                onOpenSettings = { openHotspotSettings() }
            )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        btnOpenSettings.setOnClickListener { openHotspotSettings() }
        btnScanNow.setOnClickListener { runManualScan() }

        if (intent.getBooleanExtra(EXTRA_OPENED_FROM_NOTIFICATION, false)) {
            VpnLogManager.info(
                "UNAUTHORIZED_CLIENT_NOTIFICATION_OPENED",
                "Provider membuka daftar dari notifikasi."
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        val allItems = HotspotBlocklistAssistant.snapshot(this)
        val visible =
            allItems.filter {
                it.authStatus == NetworkAccessPolicy.AuthStatus.UNKNOWN_NO_HELLO_AUTH ||
                    it.authStatus == NetworkAccessPolicy.AuthStatus.UNAUTHORIZED ||
                    it.authStatus == NetworkAccessPolicy.AuthStatus.EXPIRED ||
                    it.trustLevel == ClientTrustLevel.SUSPICIOUS ||
                    it.trustLevel == ClientTrustLevel.BLOCKED
            }.filterNot {
                it.trustLevel == ClientTrustLevel.MANUAL_APPROVED || it.manuallyAllowed
            }
        visible.forEach {
            VpnLogManager.info(
                "UNAUTHORIZED_CLIENT_CARD_CREATED",
                "client=${it.ipAddress} trust=${it.trustLevel.name} auth=${it.authStatus.name}"
            )
        }
        adapter.submitItems(
            visible.mapIndexed { index, model ->
                UnauthorizedClientCardAdapter.CardItem(
                    model = model,
                    identity = ClientIdentityMatcher.buildDisplayIdentity(this, index, model)
                )
            }
        )
        txtEmpty.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        val suspiciousCount = visible.count { it.trustLevel == ClientTrustLevel.SUSPICIOUS }
        val blockedCount = visible.count { it.trustLevel == ClientTrustLevel.BLOCKED }
        txtSummary.text =
            "Tidak Diizinkan: ${visible.size}\nSuspicious: $suspiciousCount\nBlocked: $blockedCount"
        txtHint.text =
            if (visible.isEmpty()) {
                "Tidak ada perangkat tidak diizinkan."
            } else {
                "Android standar mungkin memerlukan blokir manual dari pengaturan hotspot.\nJika perangkat belum muncul, buka browser di PC lalu akses http://gateway-ip:8080 atau ping gateway agar perangkat terlihat."
            }
        VpnLogManager.info("UNAUTHORIZED_CLIENT_LIST_UPDATED", "visible=${visible.size}")
    }

    private fun runManualScan() {
        VpnLogManager.info("MANUAL_HOTSPOT_SCAN_REQUESTED", "source=UnauthorizedClientsActivity")
        lifecycleScope.launch {
            HotspotNetworkScanner.scan(this@UnauthorizedClientsActivity)
            refreshList()
            Toast.makeText(
                this@UnauthorizedClientsActivity,
                "Scan hotspot selesai. Daftar diperbarui.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun blockClient(model: UnauthorizedClientUiModel) {
        ProviderClientActionManager.markBlocked(this, model.ipAddress)
        UnauthorizedClientAlertManager.showBlockedDialog(this) {
            openHotspotSettings()
        }
        refreshList()
    }

    private fun allowClient(model: UnauthorizedClientUiModel) {
        ProviderClientActionManager.approveManual(this, model.ipAddress)
        Toast.makeText(this, "Perangkat diizinkan manual.", Toast.LENGTH_SHORT).show()
        refreshList()
    }

    private fun copyMac(model: UnauthorizedClientUiModel) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "ghalbit_unauthorized_client_mac",
                model.macAddress ?: "-"
            )
        )
        Toast.makeText(this, "MAC disalin.", Toast.LENGTH_SHORT).show()
    }

    private fun copyIp(model: UnauthorizedClientUiModel) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "ghalbit_unauthorized_client_ip",
                model.ipAddress
            )
        )
        Toast.makeText(this, "IP disalin.", Toast.LENGTH_SHORT).show()
    }

    private fun openHotspotSettings() {
        VpnLogManager.info("HOTSPOT_BLOCK_SETTINGS_REQUESTED", "unauthorized_clients_activity")
        VpnLogManager.warn(
            "BLOCKLIST_MANUAL_ACTION_REQUIRED",
            "Android standar mungkin memerlukan blokir manual dari pengaturan hotspot."
        )
        if (!HotspotSettingsNavigator.openHotspotSettings(this)) {
            Toast.makeText(this, "Pengaturan hotspot belum bisa dibuka di perangkat ini.", Toast.LENGTH_SHORT).show()
        }
    }
}
