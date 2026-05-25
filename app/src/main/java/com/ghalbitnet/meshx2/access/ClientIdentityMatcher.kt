package com.ghalbitnet.meshx2.access

import android.content.Context
import com.ghalbitnet.meshx2.vpn.VpnLogManager

object ClientIdentityMatcher {

    fun buildDisplayIdentity(
        context: Context,
        index: Int,
        model: UnauthorizedClientUiModel
    ): ClientDisplayIdentity {
        val resolvedName =
            DeviceNameResolver.resolve(
                context = context,
                ipAddress = model.ipAddress,
                fallbackName = model.deviceName
            )
        if (!model.macAddress.isNullOrBlank()) {
            VpnLogManager.info(
                "CLIENT_MAC_RESOLVED",
                "ip=${model.ipAddress} mac=${model.macAddress}"
            )
        }
        val identity =
            ClientDisplayIdentity(
                displayNumber = index + 1,
                displayName = resolvedName,
                ipAddress = model.ipAddress,
                macAddress = model.macAddress,
                shortMac = model.macAddress?.takeLast(8),
                authStatus = model.authStatus,
                trustLevel = model.trustLevel,
                lastSeen = model.lastSeen
            )
        VpnLogManager.info(
            "CLIENT_DISPLAY_IDENTITY_CREATED",
            "number=#${identity.displayNumber} ip=${identity.ipAddress} mac=${identity.macAddress ?: "-"} name=${identity.displayName ?: "-"}"
        )
        VpnLogManager.info(
            "CLIENT_IDENTITY_MATCH_HINT_READY",
            "Cocokkan ${identity.macAddress ?: identity.ipAddress} dengan daftar hotspot Android."
        )
        return identity
    }
}
