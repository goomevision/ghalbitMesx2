package com.ghalbitnet.meshx2.access

import android.content.Context
import com.ghalbitnet.meshx2.vpn.VpnLogManager

object ProviderClientActionManager {

    fun markTrusted(context: Context, clientIp: String, note: String = "Provider menandai trusted.") {
        updateTrust(context, clientIp) { existing ->
            existing.copy(
                trustLevel = ClientTrustLevel.TRUSTED.name,
                trustScore = 95,
                isSuspicious = false,
                isBlocked = false,
                providerNote = note,
                updatedAt = System.currentTimeMillis()
            )
        }
        logAction(context, ProviderQuickAction.TRUSTED, clientIp, null, note)
        VpnLogManager.info("PROVIDER_ACTION_TRUSTED", "client=$clientIp note=$note")
        VpnLogManager.info("CLIENT_MARKED_TRUSTED", "client=$clientIp")
    }

    fun markSuspicious(context: Context, clientIp: String, note: String = "Provider menandai suspicious.") {
        updateTrust(context, clientIp) { existing ->
            existing.copy(
                trustLevel = ClientTrustLevel.SUSPICIOUS.name,
                trustScore = 20,
                isSuspicious = true,
                providerNote = note,
                updatedAt = System.currentTimeMillis()
            )
        }
        logAction(context, ProviderQuickAction.SUSPICIOUS, clientIp, null, note)
        VpnLogManager.info("PROVIDER_ACTION_SUSPICIOUS", "client=$clientIp note=$note")
        VpnLogManager.info("CLIENT_MARKED_SUSPICIOUS", "client=$clientIp")
    }

    fun markBlocked(context: Context, clientIp: String, note: String = "Provider memblokir manual.") {
        val nodeId = UnauthorizedClientRegistry.snapshot(clientIp)?.nodeId ?: HotspotClientSessionManager.get(clientIp)?.nodeId
        if (!nodeId.isNullOrBlank()) {
            PeerAuthRegistry.markBlocked(nodeId, note)
            AccessTokenManager.expireToken(nodeId)
        }
        updateTrust(context, clientIp) { existing ->
            existing.copy(
                nodeId = nodeId ?: existing.nodeId,
                trustLevel = ClientTrustLevel.BLOCKED.name,
                trustScore = 0,
                isSuspicious = true,
                isBlocked = true,
                providerNote = note,
                updatedAt = System.currentTimeMillis()
            )
        }
        UnauthorizedClientRegistry.markStatus(clientIp, NetworkAccessPolicy.AuthStatus.BLOCKED, nodeId, note)
        logAction(context, ProviderQuickAction.BLOCKED, clientIp, nodeId, note)
        VpnLogManager.info("PROVIDER_ACTION_BLOCKED", "client=$clientIp note=$note")
        VpnLogManager.info("CLIENT_MARKED_BLOCKED", "client=$clientIp")
    }

    fun approveManual(context: Context, clientIp: String, note: String = "Provider mengizinkan manual.") {
        HotspotBlocklistAssistant.markManualAllowed(context, clientIp)
        updateTrust(context, clientIp) { existing ->
            existing.copy(
                trustLevel = ClientTrustLevel.MANUAL_APPROVED.name,
                trustScore = existing.trustScore.coerceAtLeast(70),
                isManualApproved = true,
                isBlocked = false,
                providerNote = note,
                updatedAt = System.currentTimeMillis()
            )
        }
        logAction(context, ProviderQuickAction.MANUAL_APPROVED, clientIp, null, note)
        VpnLogManager.info("PROVIDER_ACTION_MANUAL_APPROVED", "client=$clientIp note=$note")
        VpnLogManager.info("CLIENT_MARKED_MANUAL_APPROVED", "client=$clientIp")
    }

    fun forceReauth(context: Context, clientIp: String, note: String = "Provider meminta re-auth.") {
        val nodeId = UnauthorizedClientRegistry.snapshot(clientIp)?.nodeId ?: HotspotClientSessionManager.get(clientIp)?.nodeId
        if (!nodeId.isNullOrBlank()) {
            AccessTokenManager.expireToken(nodeId)
        }
        UnauthorizedClientRegistry.markStatus(clientIp, NetworkAccessPolicy.AuthStatus.AUTH_PENDING, nodeId, note)
        HotspotClientSessionManager.upsert(clientIp, nodeId, GatewayClientPolicy.ClientStatus.UNKNOWN, note, null)
        logAction(context, ProviderQuickAction.FORCE_REAUTH, clientIp, nodeId, note)
        VpnLogManager.info("PROVIDER_ACTION_FORCE_REAUTH", "client=$clientIp note=$note")
    }

    fun invalidateToken(context: Context, nodeId: String?, clientIp: String, note: String = "Provider invalidasi token.") {
        if (!nodeId.isNullOrBlank()) {
            AccessTokenManager.expireToken(nodeId)
        }
        UnauthorizedClientRegistry.markStatus(clientIp, NetworkAccessPolicy.AuthStatus.EXPIRED, nodeId, note)
        logAction(context, ProviderQuickAction.INVALIDATE_TOKEN, clientIp, nodeId, note)
        VpnLogManager.info("PROVIDER_ACTION_TOKEN_INVALIDATED", "client=$clientIp nodeId=${nodeId ?: "-"}")
    }

    fun recommendPasswordRotation(context: Context, note: String = "Provider disarankan rotasi password hotspot.") {
        logAction(context, ProviderQuickAction.PASSWORD_ROTATION_RECOMMENDED, "-", null, note)
        VpnLogManager.info("PROVIDER_ACTION_PASSWORD_ROTATION_RECOMMENDED", note)
    }

    private fun updateTrust(context: Context, clientIp: String, transform: (ClientTrustEntity) -> ClientTrustEntity) {
        val existing =
            CommunitySessionRepository.cachedTrust(clientIp)
                ?: ClientTrustEntity(
                    clientIp = clientIp,
                    nodeId = UnauthorizedClientRegistry.snapshot(clientIp)?.nodeId
                        ?: HotspotClientSessionManager.get(clientIp)?.nodeId
                )
        val updated = transform(existing)
        CommunitySessionRepository.saveTrust(context, updated)
        VpnLogManager.info("CLIENT_TRUST_LEVEL_CHANGED", "client=$clientIp level=${updated.trustLevel} score=${updated.trustScore}")
        CommunitySessionRepository.syncFromCurrentState(context)
    }

    private fun logAction(context: Context, action: ProviderQuickAction, clientIp: String, nodeId: String?, note: String) {
        CommunitySessionRepository.logAction(
            context,
            ProviderActionLogEntity(
                actionType = action.name,
                clientIp = clientIp,
                nodeId = nodeId,
                note = note,
                providerId = CommunitySessionRepository.providerId(context)
            )
        )
    }
}
