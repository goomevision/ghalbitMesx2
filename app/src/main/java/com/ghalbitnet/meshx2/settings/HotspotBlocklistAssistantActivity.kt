package com.ghalbitnet.meshx2.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.access.ClientTrustLevel
import com.ghalbitnet.meshx2.access.CommunitySessionActivity
import com.ghalbitnet.meshx2.access.HotspotBlocklistAssistant
import com.ghalbitnet.meshx2.access.HotspotPasswordRotationAdvisor
import com.ghalbitnet.meshx2.access.ProviderClientActionManager
import com.ghalbitnet.meshx2.access.UnauthorizedClientUiModel
import com.ghalbitnet.meshx2.vpn.VpnLogManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HotspotBlocklistAssistantActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FOCUS_UNAUTHORIZED = "focus_unauthorized"
    }

    private lateinit var txtSectionTitle: TextView
    private lateinit var txtEmpty: TextView
    private lateinit var txtPasswordRecommendation: TextView
    private lateinit var btnOpenHotspotSettings: Button
    private lateinit var btnOpenCommunitySessions: Button
    private lateinit var btnRecommendPasswordRotation: Button
    private lateinit var btnFilterAll: Button
    private lateinit var btnFilterUnauthorized: Button
    private lateinit var btnFilterSuspicious: Button
    private lateinit var btnFilterBlocked: Button
    private lateinit var btnFilterManualApproved: Button
    private lateinit var btnFilterTrusted: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: HotspotClientAdapter

    private var activeFilter: HotspotBlocklistAssistant.Filter = HotspotBlocklistAssistant.Filter.UNAUTHORIZED
    private var allClients: List<UnauthorizedClientUiModel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hotspot_blocklist_assistant)

        txtSectionTitle = findViewById(R.id.txtUnauthorizedSectionTitle)
        txtEmpty = findViewById(R.id.txtHotspotClientEmpty)
        txtPasswordRecommendation = findViewById(R.id.txtPasswordRecommendation)
        btnOpenHotspotSettings = findViewById(R.id.btnOpenHotspotSettingsAssistant)
        btnOpenCommunitySessions = findViewById(R.id.btnOpenCommunitySessionsAssistant)
        btnRecommendPasswordRotation = findViewById(R.id.btnRecommendPasswordRotation)
        btnFilterAll = findViewById(R.id.btnFilterAllClients)
        btnFilterUnauthorized = findViewById(R.id.btnFilterUnauthorizedClients)
        btnFilterSuspicious = findViewById(R.id.btnFilterSuspiciousClients)
        btnFilterBlocked = findViewById(R.id.btnFilterBlockedClients)
        btnFilterManualApproved = findViewById(R.id.btnFilterManualApprovedClients)
        btnFilterTrusted = findViewById(R.id.btnFilterTrustedClients)
        recyclerView = findViewById(R.id.rvHotspotClients)

        adapter =
            HotspotClientAdapter(
                onCopy = ::copyClient,
                onAllowManual = ::markManualAllowed,
                onBlocked = ::markBlocked,
                onOpenSettings = ::openHotspotSettings,
                onTrusted = ::markTrusted,
                onSuspicious = ::markSuspicious
            )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        if (intent.getBooleanExtra(EXTRA_FOCUS_UNAUTHORIZED, false)) {
            activeFilter = HotspotBlocklistAssistant.Filter.UNAUTHORIZED
        }

        VpnLogManager.info("HOTSPOT_BLOCKLIST_ASSISTANT_OPENED", "Provider membuka asisten blocklist hotspot.")
        VpnLogManager.info("UNAUTHORIZED_CLIENT_LIST_OPENED", "Filter awal=${activeFilter.name}")

        btnOpenHotspotSettings.setOnClickListener { openHotspotSettings(null) }
        btnOpenCommunitySessions.setOnClickListener {
            startActivity(Intent(this, CommunitySessionActivity::class.java))
        }
        btnRecommendPasswordRotation.setOnClickListener {
            val recommendation = HotspotPasswordRotationAdvisor.evaluate(this, forced = true)
            ProviderClientActionManager.recommendPasswordRotation(this, recommendation.reason)
            txtPasswordRecommendation.text =
                if (recommendation.shouldRecommend) {
                    "Disarankan mengganti password hotspot.\n${recommendation.reason}\nPassword baru: ${recommendation.suggestedPassword}"
                } else {
                    recommendation.reason
                }
            HotspotSettingsNavigator.openForPasswordChange(this)
        }

        btnFilterAll.setOnClickListener { applyFilter(HotspotBlocklistAssistant.Filter.ALL) }
        btnFilterUnauthorized.setOnClickListener { applyFilter(HotspotBlocklistAssistant.Filter.UNAUTHORIZED) }
        btnFilterSuspicious.setOnClickListener { applyFilter(HotspotBlocklistAssistant.Filter.SUSPICIOUS) }
        btnFilterBlocked.setOnClickListener { applyFilter(HotspotBlocklistAssistant.Filter.BLOCKED) }
        btnFilterManualApproved.setOnClickListener { applyFilter(HotspotBlocklistAssistant.Filter.MANUAL_APPROVED) }
        btnFilterTrusted.setOnClickListener { applyFilter(HotspotBlocklistAssistant.Filter.TRUSTED) }
    }

    override fun onResume() {
        super.onResume()
        refreshClients()
    }

    private fun refreshClients() {
        allClients = HotspotBlocklistAssistant.snapshot(this)
        applyFilter(activeFilter, emitLog = false)
        val recommendation = HotspotPasswordRotationAdvisor.evaluate(this)
        txtPasswordRecommendation.text =
            if (recommendation.shouldRecommend) {
                "Disarankan mengganti password hotspot.\n${recommendation.reason}\nPassword baru: ${recommendation.suggestedPassword}"
            } else {
                recommendation.reason
            }
    }

    private fun applyFilter(
        filter: HotspotBlocklistAssistant.Filter,
        emitLog: Boolean = true
    ) {
        activeFilter = filter
        val filtered =
            when (filter) {
                HotspotBlocklistAssistant.Filter.UNAUTHORIZED ->
                    allClients.filter {
                        (!it.manuallyAllowed &&
                            it.trustLevel != ClientTrustLevel.BLOCKED &&
                            it.trustLevel != ClientTrustLevel.TRUSTED &&
                            it.trustLevel != ClientTrustLevel.MANUAL_APPROVED) ||
                            it.trustLevel == ClientTrustLevel.SUSPICIOUS
                    }
                else -> HotspotBlocklistAssistant.applyFilter(allClients, filter)
            }
        adapter.submitItems(filtered)
        txtEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        txtSectionTitle.text =
            if (filter == HotspotBlocklistAssistant.Filter.UNAUTHORIZED) {
                getString(R.string.hotspot_blocklist_section_unauthorized)
            } else {
                getString(R.string.hotspot_blocklist_section_filtered, filter.name)
            }
        if (emitLog) {
            VpnLogManager.info(
                "UNAUTHORIZED_CLIENT_FILTER_APPLIED",
                "filter=${filter.name} size=${filtered.size}"
            )
        }
    }

    private fun openHotspotSettings(model: UnauthorizedClientUiModel?) {
        VpnLogManager.warn(
            "BLOCKLIST_MANUAL_ACTION_REQUIRED",
            "Android standar mungkin memerlukan blokir manual dari pengaturan hotspot."
        )
        VpnLogManager.info(
            "HOTSPOT_BLOCK_SETTINGS_REQUESTED",
            "client=${model?.ipAddress ?: "-"}"
        )
        if (!HotspotSettingsNavigator.openHotspotSettings(this)) {
            Toast.makeText(this, "Pengaturan hotspot belum bisa dibuka di perangkat ini.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyClient(model: UnauthorizedClientUiModel) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "ghalbit_hotspot_client",
                HotspotBlocklistAssistant.buildCopyPayload(model)
            )
        )
        VpnLogManager.info("UNAUTHORIZED_CLIENT_REVIEWED", "client=${model.ipAddress} action=COPY")
        Toast.makeText(this, "IP/MAC klien disalin.", Toast.LENGTH_SHORT).show()
    }

    private fun markManualAllowed(model: UnauthorizedClientUiModel) {
        ProviderClientActionManager.approveManual(this, model.ipAddress)
        Toast.makeText(this, "Klien ditandai diizinkan manual.", Toast.LENGTH_SHORT).show()
        refreshClients()
    }

    private fun markTrusted(model: UnauthorizedClientUiModel) {
        ProviderClientActionManager.markTrusted(this, model.ipAddress)
        refreshClients()
    }

    private fun markSuspicious(model: UnauthorizedClientUiModel) {
        ProviderClientActionManager.markSuspicious(this, model.ipAddress)
        refreshClients()
    }

    private fun markBlocked(model: UnauthorizedClientUiModel) {
        ProviderClientActionManager.markBlocked(this, model.ipAddress)
        refreshClients()
    }

    private class HotspotClientAdapter(
        private val onCopy: (UnauthorizedClientUiModel) -> Unit,
        private val onAllowManual: (UnauthorizedClientUiModel) -> Unit,
        private val onBlocked: (UnauthorizedClientUiModel) -> Unit,
        private val onOpenSettings: (UnauthorizedClientUiModel?) -> Unit,
        private val onTrusted: (UnauthorizedClientUiModel) -> Unit,
        private val onSuspicious: (UnauthorizedClientUiModel) -> Unit
    ) : RecyclerView.Adapter<HotspotClientAdapter.HotspotClientViewHolder>() {

        private val items = mutableListOf<UnauthorizedClientUiModel>()

        fun submitItems(next: List<UnauthorizedClientUiModel>) {
            items.clear()
            items.addAll(next)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HotspotClientViewHolder {
            val view =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_hotspot_client, parent, false)
            return HotspotClientViewHolder(view, onCopy, onAllowManual, onBlocked, onOpenSettings, onTrusted, onSuspicious)
        }

        override fun onBindViewHolder(holder: HotspotClientViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        class HotspotClientViewHolder(
            itemView: View,
            private val onCopy: (UnauthorizedClientUiModel) -> Unit,
            private val onAllowManual: (UnauthorizedClientUiModel) -> Unit,
            private val onBlocked: (UnauthorizedClientUiModel) -> Unit,
            private val onOpenSettings: (UnauthorizedClientUiModel?) -> Unit,
            private val onTrusted: (UnauthorizedClientUiModel) -> Unit,
            private val onSuspicious: (UnauthorizedClientUiModel) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {

            private val txtTitle: TextView = itemView.findViewById(R.id.txtHotspotClientTitle)
            private val txtMeta: TextView = itemView.findViewById(R.id.txtHotspotClientMeta)
            private val txtStatus: TextView = itemView.findViewById(R.id.txtHotspotClientStatus)
            private val btnBlocked: Button = itemView.findViewById(R.id.btnBlockHotspotClient)
            private val btnOpenSettings: Button = itemView.findViewById(R.id.btnOpenBlocklistHotspotClient)
            private val btnCopy: Button = itemView.findViewById(R.id.btnCopyHotspotClient)
            private val btnAllowManual: Button = itemView.findViewById(R.id.btnAllowHotspotClient)
            private val btnTrusted: Button = itemView.findViewById(R.id.btnTrustHotspotClient)
            private val btnSuspicious: Button = itemView.findViewById(R.id.btnSuspiciousHotspotClient)

            fun bind(model: UnauthorizedClientUiModel) {
                txtTitle.text = model.ipAddress
                txtMeta.text =
                    buildString {
                        append("MAC: ${model.macAddress ?: "-"}")
                        append("\nDevice: ${model.deviceName ?: "-"}")
                        append("\nFirst seen: ${formatTime(model.firstSeen)}")
                        append("\nLast seen: ${formatTime(model.lastSeen)}")
                    }
                txtStatus.text =
                    buildString {
                        append("HELLO_AUTH: ${model.authStatus.name}")
                        append("\nTrust: ${model.trustLevel.name}")
                        append("\nAlasan: ${model.reason}")
                        append("\n${model.detail}")
                        if (model.manuallyAllowed) {
                            append("\nDiizinkan manual oleh provider.")
                        }
                    }

                btnBlocked.setOnClickListener { onBlocked(model) }
                btnOpenSettings.setOnClickListener { onOpenSettings(model) }
                btnCopy.setOnClickListener { onCopy(model) }
                btnAllowManual.setOnClickListener { onAllowManual(model) }
                btnTrusted.setOnClickListener { onTrusted(model) }
                btnSuspicious.setOnClickListener { onSuspicious(model) }
                btnBlocked.isEnabled = model.trustLevel != ClientTrustLevel.BLOCKED
            }

            private fun formatTime(timeMillis: Long): String =
                SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()).format(Date(timeMillis))
        }
    }
}
