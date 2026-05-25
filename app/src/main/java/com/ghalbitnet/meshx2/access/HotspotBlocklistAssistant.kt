package com.ghalbitnet.meshx2.access

import android.content.Context
import com.ghalbitnet.meshx2.vpn.VpnLogManager

object HotspotBlocklistAssistant {

    enum class Filter {
        ALL,
        UNAUTHORIZED,
        SUSPICIOUS,
        BLOCKED,
        MANUAL_APPROVED,
        TRUSTED
    }

    private const val PREFS_NAME = "ghalbit_hotspot_blocklist_assistant"
    private const val KEY_MANUAL_ALLOWED_IPS = "manual_allowed_ips"

    fun snapshot(context: Context): List<UnauthorizedClientUiModel> {
        val manualAllowed = manualAllowedIps(context)
        CommunitySessionRepository.syncFromCurrentState(context)
        return UnauthorizedClientRegistry.all()
            .filter { it.status != NetworkAccessPolicy.AuthStatus.AUTHORIZED }
            .map { record ->
                val trustEntity = CommunitySessionRepository.cachedTrust(record.ipAddress)
                val hotspotSession = HotspotClientSessionManager.get(record.ipAddress)
                val communitySession = CommunitySessionRepository.cachedSession(record.ipAddress)
                UnauthorizedClientUiModel(
                    ipAddress = record.ipAddress,
                    macAddress = record.macAddress ?: lookupPossibleMac(record.ipAddress),
                    deviceName = record.deviceName ?: communitySession?.nodeId ?: record.nodeId,
                    authStatus = record.status,
                    trustLevel = trustEntity?.trustLevel?.let { ClientTrustLevel.valueOf(it) } ?: ClientTrustLevel.UNKNOWN,
                    accessTokenStatus = when {
                        hotspotSession?.accessToken?.isNotBlank() == true -> "VALID"
                        record.status == NetworkAccessPolicy.AuthStatus.EXPIRED -> "EXPIRED"
                        else -> "MISSING"
                    },
                    firstSeen = communitySession?.firstSeen ?: record.firstSeen,
                    lastSeen = communitySession?.lastSeen ?: record.lastSeen,
                    reason = reasonFor(record.status, hotspotSession?.accessToken, trustEntity),
                    detail = record.detail,
                    detectedAt = record.lastSeen,
                    nodeId = record.nodeId,
                    manuallyAllowed = manualAllowed.contains(record.ipAddress)
                )
            }
    }

    fun applyFilter(
        items: List<UnauthorizedClientUiModel>,
        filter: Filter
    ): List<UnauthorizedClientUiModel> =
        when (filter) {
            Filter.ALL -> items
            Filter.UNAUTHORIZED ->
                items.filter {
                    !it.manuallyAllowed &&
                        it.trustLevel != ClientTrustLevel.TRUSTED &&
                        it.trustLevel != ClientTrustLevel.MANUAL_APPROVED &&
                        it.trustLevel != ClientTrustLevel.BLOCKED
                }
            Filter.SUSPICIOUS -> items.filter { it.trustLevel == ClientTrustLevel.SUSPICIOUS }
            Filter.BLOCKED -> items.filter { it.trustLevel == ClientTrustLevel.BLOCKED }
            Filter.MANUAL_APPROVED -> items.filter { it.manuallyAllowed || it.trustLevel == ClientTrustLevel.MANUAL_APPROVED }
            Filter.TRUSTED -> items.filter { it.trustLevel == ClientTrustLevel.TRUSTED || it.trustLevel == ClientTrustLevel.COMMUNITY_VERIFIED }
        }

    data class Summary(
        val unauthorizedCount: Int,
        val manualAllowedCount: Int,
        val latestDetectedAt: Long?
    )

    fun summary(context: Context): Summary {
        val items = snapshot(context)
        return Summary(
            unauthorizedCount = items.size,
            manualAllowedCount = items.count { it.manuallyAllowed },
            latestDetectedAt = items.maxOfOrNull { it.detectedAt }
        )
    }

    fun markManualAllowed(context: Context, ipAddress: String) {
        val next = manualAllowedIps(context).toMutableSet()
        next.add(ipAddress)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_MANUAL_ALLOWED_IPS, next)
            .apply()
        VpnLogManager.info(
            "UNAUTHORIZED_CLIENT_REVIEWED",
            "client=$ipAddress action=MANUAL_ALLOWED"
        )
    }

    fun buildCopyPayload(model: UnauthorizedClientUiModel): String {
        return buildString {
            append("Nama: ${model.deviceName ?: "-"}")
            append("\n")
            append("IP: ${model.ipAddress}")
            append("\nMAC: ${model.macAddress ?: "-"}")
            append("\nStatus HELLO_AUTH: ${model.authStatus.name}")
            append("\nNode: ${model.nodeId ?: "-"}")
            append("\nDetail: ${model.detail}")
        }
    }

    fun lookupPossibleMacPublic(ipAddress: String): String? = lookupPossibleMac(ipAddress)

    private fun manualAllowedIps(context: Context): Set<String> {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_MANUAL_ALLOWED_IPS, emptySet())
            ?.toSet()
            ?: emptySet()
    }

    private fun reasonFor(
        authStatus: NetworkAccessPolicy.AuthStatus,
        accessToken: String?,
        trustEntity: ClientTrustEntity?
    ): String =
        when {
            trustEntity?.isBlocked == true -> "Ditandai blocked oleh provider."
            trustEntity?.isSuspicious == true -> "Ditandai suspicious oleh provider."
            trustEntity?.isManualApproved == true -> "Sudah disetujui manual oleh provider."
            authStatus == NetworkAccessPolicy.AuthStatus.UNKNOWN_NO_HELLO_AUTH -> "Tidak ada HELLO_AUTH."
            authStatus == NetworkAccessPolicy.AuthStatus.UNKNOWN_DEVICE -> "Tidak punya HELLO_AUTH."
            authStatus == NetworkAccessPolicy.AuthStatus.UNAUTHORIZED -> "Tidak punya HELLO_AUTH atau signature tidak sah."
            authStatus == NetworkAccessPolicy.AuthStatus.EXPIRED -> "Token tidak valid atau sudah kedaluwarsa."
            accessToken.isNullOrBlank() -> "Tidak ada ACCESS_TOKEN."
            else -> "Belum disetujui provider."
        }

    private fun lookupPossibleMac(ipAddress: String): String? {
        return runCatching {
            val arpFile = java.io.File("/proc/net/arp")
            if (!arpFile.exists()) return null
            arpFile.useLines { lines ->
                lines.drop(1)
                    .map { it.trim().split(Regex("\\s+")) }
                    .firstOrNull { it.size >= 4 && it[0] == ipAddress }
                    ?.getOrNull(3)
                    ?.takeIf { it.isNotBlank() && it != "00:00:00:00:00:00" }
            }
        }.getOrNull()
    }
}
