package com.ghalbitnet.meshx2.access

import android.content.Context
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.vpn.VpnLogManager

object UnauthorizedHotspotWarning {

    data class WarningState(
        val shouldWarn: Boolean,
        val message: String,
        val detail: String
    )

    fun evaluate(context: Context): WarningState {
        val snapshot = HotspotClientDetector.snapshot(context)
        if (!snapshot.unauthorizedActive) {
            return WarningState(
                shouldWarn = false,
                message = "",
                detail = ""
            )
        }
        val advisor = ProviderSecurityAdvisor.advise(context, snapshot)
        val observation =
            snapshot.observations.firstOrNull()?.let { obs ->
                if (obs.nodeId.isNullOrBlank()) {
                    "ip=${obs.ipAddress} detail=${obs.detail}"
                } else {
                    "node=${obs.nodeId} detail=${obs.detail}"
                }
            }.orEmpty()
        VpnLogManager.warn(
            "HOTSPOT_UNAUTHORIZED_CLIENT_DETECTED",
            "authorized=${snapshot.authorizedPeerCount} unauthorized=${snapshot.unauthorizedObservationCount} $observation"
        )
        VpnLogManager.warn(
            "HOTSPOT_CLIENT_NOT_ENFORCEABLE_ON_STOCK_ANDROID",
            "Perangkat asing terhubung. Android standar belum bisa memblokir klien hotspot tanpa mode gateway/device-owner/router."
        )
        return WarningState(
            shouldWarn = true,
            message = context.getString(R.string.hotspot_security_warning_message),
            detail = advisor.detail
        )
    }
}
