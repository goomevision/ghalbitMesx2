package com.ghalbitnet.meshx2.access

import android.content.Context
import com.ghalbitnet.meshx2.vpn.VpnLogManager

object ProviderNetworkStatusEvaluator {

    data class Snapshot(
        val status: ProviderNetworkStatus,
        val title: String,
        val detail: String,
        val unauthorizedCount: Int,
        val suspiciousCount: Int,
        val blockedCount: Int
    )

    fun evaluate(context: Context): Snapshot {
        val clients = HotspotBlocklistAssistant.snapshot(context)
        val unauthorizedCount = clients.count { !it.manuallyAllowed && it.authStatus != NetworkAccessPolicy.AuthStatus.AUTHORIZED }
        val suspiciousCount = clients.count { it.trustLevel == ClientTrustLevel.SUSPICIOUS || it.trustLevel == ClientTrustLevel.BLOCKED }
        val blockedCount = clients.count { it.trustLevel == ClientTrustLevel.BLOCKED }
        val status =
            when {
                blockedCount > 0 -> ProviderNetworkStatus.DANGER
                suspiciousCount > 0 || unauthorizedCount > 0 -> ProviderNetworkStatus.RISK
                else -> ProviderNetworkStatus.SAFE
            }
        when (status) {
            ProviderNetworkStatus.SAFE -> VpnLogManager.info("PROVIDER_STATUS_SAFE", "Tidak ada risiko hotspot aktif.")
            ProviderNetworkStatus.WARNING -> VpnLogManager.info("PROVIDER_STATUS_WARNING", "Ada sedikit client asing.")
            ProviderNetworkStatus.RISK -> VpnLogManager.info("PROVIDER_STATUS_RISK", "Ada client asing mencurigakan atau cukup banyak unauthorized.")
            ProviderNetworkStatus.DANGER -> VpnLogManager.info("PROVIDER_STATUS_DANGER", "Ada client blocked/suspicious berat.")
        }
        val title =
            when (status) {
                ProviderNetworkStatus.SAFE -> "Aman"
                ProviderNetworkStatus.WARNING -> "Waspada"
                ProviderNetworkStatus.RISK -> "Risiko"
                ProviderNetworkStatus.DANGER -> "Bahaya"
            }
        val detail = "Unauthorized: $unauthorizedCount | Suspicious: $suspiciousCount | Blocked: $blockedCount"
        return Snapshot(status, title, detail, unauthorizedCount, suspiciousCount, blockedCount)
    }
}
