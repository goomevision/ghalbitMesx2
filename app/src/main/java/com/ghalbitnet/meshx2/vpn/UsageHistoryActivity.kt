package com.ghalbitnet.meshx2.vpn

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ghalbitnet.meshx2.R
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max

class UsageHistoryActivity : AppCompatActivity() {

    private val viewModel: UsageSummaryViewModel by viewModels()

    private lateinit var txtLatestSession: TextView
    private lateinit var txtUsageCostPreview: TextView
    private lateinit var txtUsageEmpty: TextView
    private lateinit var btnUsageRefresh: Button
    private lateinit var btnUsageCloseActive: Button
    private lateinit var btnUsageExport: Button
    private lateinit var rvUsageHistory: RecyclerView
    private lateinit var usageHistoryAdapter: UsageHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usage_history)

        txtLatestSession = findViewById(R.id.txtLatestUsageSession)
        txtUsageCostPreview = findViewById(R.id.txtUsageCostPreview)
        txtUsageEmpty = findViewById(R.id.txtUsageEmpty)
        btnUsageRefresh = findViewById(R.id.btnUsageRefresh)
        btnUsageCloseActive = findViewById(R.id.btnUsageCloseActive)
        btnUsageExport = findViewById(R.id.btnUsageExport)
        rvUsageHistory = findViewById(R.id.rvUsageHistory)

        usageHistoryAdapter = UsageHistoryAdapter()
        rvUsageHistory.layoutManager = LinearLayoutManager(this)
        rvUsageHistory.adapter = usageHistoryAdapter

        VpnLogManager.info("USAGE_UI_OPENED", "Halaman ringkasan usage lokal dibuka.")

        btnUsageRefresh.setOnClickListener {
            viewModel.refresh()
        }
        btnUsageCloseActive.setOnClickListener {
            viewModel.closeActiveSession()
        }
        btnUsageExport.setOnClickListener {
            lifecycleScope.launch {
                val state = viewModel.uiState().value
                shareDebugSummary(state?.exportSummary.orEmpty())
            }
        }

        viewModel.uiState().observe(this) { state ->
            txtLatestSession.text = formatLatestSession(state.latestSession)
            txtUsageCostPreview.text = formatCostPreview(state.costPreview)
            VpnLogManager.info(
                "USAGE_COST_PREVIEW_RENDERED",
                String.format(
                    Locale.getDefault(),
                    "totalMb=%.4f estimated=%.6f",
                    state.costPreview.totalMb,
                    state.costPreview.estimatedCostGbht
                )
            )
            usageHistoryAdapter.submitItems(state.recentSessions)
            txtUsageEmpty.text =
                if (state.empty) {
                    getString(R.string.usage_history_empty)
                } else {
                    ""
                }
            txtUsageEmpty.visibility = if (state.empty) View.VISIBLE else View.GONE
            btnUsageCloseActive.isEnabled = state.latestSession?.endTime == null
            if (state.closeSessionMessage != null) {
                Toast.makeText(this, state.closeSessionMessage, Toast.LENGTH_SHORT).show()
                viewModel.consumeCloseSessionMessage()
            }
        }

        viewModel.refresh()
    }

    private fun shareDebugSummary(summary: String) {
        if (summary.isBlank()) {
            Toast.makeText(this, getString(R.string.usage_history_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.usage_history_export_title))
                putExtra(Intent.EXTRA_TEXT, summary)
            }
        startActivity(Intent.createChooser(intent, getString(R.string.usage_history_export_title)))
    }

    private fun formatLatestSession(session: UsageSessionEntity?): String {
        if (session == null) {
            return getString(R.string.usage_history_empty)
        }
        return buildString {
            append("Session: ${session.sessionId}\n")
            append("Node: ${session.nodeId}\n")
            append("Mode: ${session.operatingMode}\n")
            append("Upload: ${formatBytes(session.totalUploadBytes)}\n")
            append("Download: ${formatBytes(session.totalDownloadBytes)}\n")
            append("Total: ${formatBytes(session.totalBytes)}\n")
            append("Durasi: ${formatDuration(session.startTime, session.endTime)}\n")
            append("Status: ${if (session.endTime == null) "Aktif" else "Selesai"}\n")
            append("Sinkron: ${if (session.isSynced) "Sudah" else "Belum"}")
        }
    }

    private fun formatCostPreview(preview: UsageCostPreview): String {
        return String.format(
            Locale.getDefault(),
            "Total usage: %.2f MB\nEstimasi biaya: %.6f GHBT\nBagian provider: %.6f GHBT\nBagian relay: %.6f GHBT\nBagian builder: %.6f GHBT",
            preview.totalMb,
            preview.estimatedCostGbht,
            preview.providerShareGbht,
            preview.relayShareGbht,
            preview.builderShareGbht
        )
    }

    private fun formatBytes(bytes: Long): String {
        val megaBytes = bytes / 1024.0 / 1024.0
        return String.format(Locale.getDefault(), "%.2f MB", megaBytes)
    }

    private fun formatDuration(startTime: Long, endTime: Long?): String {
        val durationMillis = max(0L, (endTime ?: System.currentTimeMillis()) - startTime)
        val totalSeconds = durationMillis / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%dh %02dm %02ds", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%dm %02ds", minutes, seconds)
        }
    }
}
