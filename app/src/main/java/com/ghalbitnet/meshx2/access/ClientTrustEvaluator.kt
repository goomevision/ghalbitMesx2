package com.ghalbitnet.meshx2.access

import android.content.Context
import com.ghalbitnet.meshx2.vpn.VpnLogManager

object ClientTrustEvaluator {

    fun evaluate(
        context: Context,
        ipAddress: String,
        nodeId: String?,
        authStatus: NetworkAccessPolicy.AuthStatus,
        accessTokenStatus: String,
        reconnectCount: Int,
        trustEntity: ClientTrustEntity?
    ): ClientTrustScore {
        val reasons = mutableListOf<String>()
        var score = 50

        if (authStatus == NetworkAccessPolicy.AuthStatus.AUTHORIZED) {
            score += 20
            reasons += "HELLO_AUTH valid"
        }
        if (accessTokenStatus == "VALID") {
            score += 15
            reasons += "ACCESS_TOKEN valid"
        }
        if (!nodeId.isNullOrBlank()) {
            score += 10
            reasons += "Peer teridentifikasi"
        }
        if (trustEntity?.isManualApproved == true) {
            score += 10
            reasons += "Diizinkan manual"
        }
        if (reconnectCount >= 4) {
            score -= 10
            reasons += "Sering reconnect"
        }
        if (
            authStatus == NetworkAccessPolicy.AuthStatus.UNAUTHORIZED ||
                authStatus == NetworkAccessPolicy.AuthStatus.UNKNOWN_DEVICE ||
                authStatus == NetworkAccessPolicy.AuthStatus.UNKNOWN_NO_HELLO_AUTH
        ) {
            score -= 20
            reasons += "Tidak punya auth"
        }
        if (trustEntity?.isSuspicious == true) {
            score -= 25
            reasons += "Ditandai mencurigakan"
        }
        if (trustEntity?.isBlocked == true || authStatus == NetworkAccessPolicy.AuthStatus.BLOCKED) {
            score = 0
            reasons += "Diblokir manual"
        }

        val level =
            when {
                trustEntity?.isBlocked == true || authStatus == NetworkAccessPolicy.AuthStatus.BLOCKED -> ClientTrustLevel.BLOCKED
                trustEntity?.trustLevel == ClientTrustLevel.TRUSTED.name -> ClientTrustLevel.TRUSTED
                trustEntity?.isSuspicious == true || score < 25 -> ClientTrustLevel.SUSPICIOUS
                authStatus == NetworkAccessPolicy.AuthStatus.AUTHORIZED && accessTokenStatus == "VALID" -> ClientTrustLevel.COMMUNITY_VERIFIED
                trustEntity?.isManualApproved == true -> ClientTrustLevel.MANUAL_APPROVED
                else -> ClientTrustLevel.UNKNOWN
            }

        val result = ClientTrustScore(score = score.coerceIn(0, 100), level = level, reasons = reasons)
        VpnLogManager.info(
            "CLIENT_TRUST_EVALUATED",
            "ip=$ipAddress nodeId=${nodeId ?: "-"} level=${result.level.name} score=${result.score} reasons=${result.reasons.joinToString("; ")}"
        )
        return result
    }
}
