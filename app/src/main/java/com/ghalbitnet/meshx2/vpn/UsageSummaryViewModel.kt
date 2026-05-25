package com.ghalbitnet.meshx2.vpn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UsageSummaryViewModel(application: Application) : AndroidViewModel(application) {

    data class UsageHistoryUiState(
        val latestSession: UsageSessionEntity? = null,
        val costPreview: UsageCostPreview = UsageCostPreview.empty(),
        val recentSessions: List<UsageSessionEntity> = emptyList(),
        val empty: Boolean = true,
        val unsyncedSessionCount: Int = 0,
        val unsyncedDeltaCount: Int = 0,
        val exportSummary: String = "",
        val closeSessionMessage: String? = null
    )

    private val uiStateLiveData = MutableLiveData(UsageHistoryUiState())

    fun uiState(): LiveData<UsageHistoryUiState> = uiStateLiveData

    init {
        UsageRepository.init(application.applicationContext)
    }

    fun refresh() {
        VpnLogManager.info("USAGE_UI_REFRESHED", "Memuat ulang ringkasan usage lokal.")
        viewModelScope.launch {
            val summary = UsageRepository.getDebugSummary(limit = 25)
            val latest = summary.activeSession ?: summary.recentSessions.firstOrNull()
            val costPreview = UsageCostCalculator.calculate(latest?.totalBytes ?: 0L)
            val export = buildDebugSummary(summary)
            uiStateLiveData.postValue(
                UsageHistoryUiState(
                    latestSession = latest,
                    costPreview = costPreview,
                    recentSessions = summary.recentSessions,
                    empty = summary.recentSessions.isEmpty(),
                    unsyncedSessionCount = summary.unsyncedSessionCount,
                    unsyncedDeltaCount = summary.unsyncedDeltaCount,
                    exportSummary = export
                )
            )
        }
    }

    fun closeActiveSession() {
        VpnLogManager.info("USAGE_UI_CLOSE_SESSION_REQUESTED", "Pengguna meminta session aktif ditutup.")
        viewModelScope.launch {
            val closed = UsageRepository.closeActiveSession()
            val summary = UsageRepository.getDebugSummary(limit = 25)
            val latest = closed ?: summary.activeSession ?: summary.recentSessions.firstOrNull()
            val costPreview = UsageCostCalculator.calculate(latest?.totalBytes ?: 0L)
            uiStateLiveData.postValue(
                UsageHistoryUiState(
                    latestSession = latest,
                    costPreview = costPreview,
                    recentSessions = summary.recentSessions,
                    empty = summary.recentSessions.isEmpty(),
                    unsyncedSessionCount = summary.unsyncedSessionCount,
                    unsyncedDeltaCount = summary.unsyncedDeltaCount,
                    exportSummary = buildDebugSummary(summary),
                    closeSessionMessage = if (closed != null) {
                        "Session aktif ditutup."
                    } else {
                        "Tidak ada session aktif."
                    }
                )
            )
        }
    }

    fun consumeCloseSessionMessage() {
        val state = uiStateLiveData.value ?: return
        if (state.closeSessionMessage == null) return
        uiStateLiveData.value = state.copy(closeSessionMessage = null)
    }

    private fun buildDebugSummary(summary: UsageRepository.UsageDebugSummary): String {
        val latest = summary.activeSession ?: summary.recentSessions.firstOrNull()
        val header = buildString {
            append("Ringkasan usage lokal GhalbitMesh X2\n")
            append("Waktu ekspor: ")
            append(formatTime(System.currentTimeMillis()))
            append("\n")
            append("Session aktif: ")
            append(latest?.sessionId ?: "-")
            append("\n")
            append("Unsynced sessions: ${summary.unsyncedSessionCount}\n")
            append("Unsynced deltas: ${summary.unsyncedDeltaCount}\n")
            val costPreview = UsageCostCalculator.calculate(latest?.totalBytes ?: 0L)
            append(
                String.format(
                    Locale.getDefault(),
                    "Estimasi biaya: %.6f GHBT | Provider %.6f | Relay %.6f | Builder %.6f\n",
                    costPreview.estimatedCostGbht,
                    costPreview.providerShareGbht,
                    costPreview.relayShareGbht,
                    costPreview.builderShareGbht
                )
            )
        }
        val sessions =
            if (summary.recentSessions.isEmpty()) {
                "\nBelum ada riwayat penggunaan."
            } else {
                summary.recentSessions.joinToString("\n\n", prefix = "\n") { session ->
                    buildString {
                        append("Session: ${session.sessionId}\n")
                        append("Node: ${session.nodeId}\n")
                        append("Mode: ${session.operatingMode}\n")
                        append("Mulai: ${formatTime(session.startTime)}\n")
                        append("Selesai: ${session.endTime?.let(::formatTime) ?: "Masih aktif"}\n")
                        append("Upload: ${formatBytes(session.totalUploadBytes)}\n")
                        append("Download: ${formatBytes(session.totalDownloadBytes)}\n")
                        append("Total: ${formatBytes(session.totalBytes)}\n")
                        append("Paket: ${session.packetCount} | TCP ${session.tcpCount} | UDP ${session.udpCount}")
                    }
                }
            }
        return header + sessions
    }

    private fun formatBytes(bytes: Long): String {
        val megaBytes = bytes / 1024.0 / 1024.0
        return String.format(Locale.getDefault(), "%.2f MB", megaBytes)
    }

    private fun formatTime(timeMillis: Long): String =
        SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault()).format(Date(timeMillis))
}
