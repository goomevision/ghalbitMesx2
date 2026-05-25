package com.ghalbitnet.meshx2.vpn

import android.content.Context
import com.ghalbitnet.meshx2.economy.InternetBridgePolicyManager

object AccessPolicyManager {

    data class AccessDecision(
        val allowed: Boolean,
        val gatewayAvailable: Boolean,
        val trustHealthy: Boolean,
        val participantValid: Boolean,
        val walletBalance: Double,
        val gatewayAddress: String,
        val gatewayId: String,
        val routeScore: Int,
        val userStatus: String,
        val gatewayName: String,
        val forwardMode: MeshForwardMode,
        val detail: String
    )

    fun evaluate(
        context: Context,
        localGlobalId: String
    ): AccessDecision {
        val mode = VpnOperatingMode.current(context)
        val policyDecision = InternetBridgePolicyManager.evaluate(context, localGlobalId)
        val gateway = GatewaySelector.current(context)
        val trustHealthy = gateway.routeScore >= 40
        val localDirectReady = LocalDirectForwarder.isReady()
        val wantsLocalDirect =
            policyDecision.routeMode == InternetBridgePolicyManager.RouteMode.LOCAL_DIRECT
        val wantsMeshGateway =
            policyDecision.routeMode == InternetBridgePolicyManager.RouteMode.REMOTE_GATEWAY
        val gatewayUsable = gateway.available && gateway.gatewayAddress.isNotBlank()

        if (mode == VpnOperatingMode.MONITORING_LIGHT) {
            return AccessDecision(
                allowed = true,
                gatewayAvailable = false,
                trustHealthy = true,
                participantValid = true,
                walletBalance = policyDecision.walletBalance,
                gatewayAddress = "",
                gatewayId = "",
                routeScore = 0,
                userStatus = policyDecision.userTier.name,
                gatewayName = "",
                forwardMode = MeshForwardMode.BLOCKED,
                detail = "MONITORING_LIGHT | ${policyDecision.detail}"
            )
        }

        if (mode == VpnOperatingMode.MONITORING_ONLY) {
            return AccessDecision(
                allowed = true,
                gatewayAvailable = gatewayUsable,
                trustHealthy = gatewayUsable && trustHealthy,
                participantValid = true,
                walletBalance = policyDecision.walletBalance,
                gatewayAddress = gateway.gatewayAddress,
                gatewayId = gateway.gatewayId,
                routeScore = gateway.routeScore,
                userStatus = policyDecision.userTier.name,
                gatewayName = gateway.gatewayName,
                forwardMode = if (gatewayUsable && wantsMeshGateway) MeshForwardMode.MESH_GATEWAY else MeshForwardMode.BLOCKED,
                detail = "MONITORING_ONLY | ${policyDecision.detail}"
            )
        }

        if (!policyDecision.allowed) {
            return AccessDecision(
                allowed = false,
                gatewayAvailable = gatewayUsable,
                trustHealthy = gatewayUsable && trustHealthy,
                participantValid = false,
                walletBalance = policyDecision.walletBalance,
                gatewayAddress = gateway.gatewayAddress,
                gatewayId = gateway.gatewayId,
                routeScore = gateway.routeScore,
                userStatus = policyDecision.userTier.name,
                gatewayName = gateway.gatewayName,
                forwardMode = MeshForwardMode.BLOCKED,
                detail = "ACCESS_BLOCKED | ${policyDecision.detail}"
            )
        }

        if (wantsLocalDirect && !localDirectReady) {
            VpnLogManager.warn(
                "VPN_ROUTE_LOCAL_DIRECT_DISABLED",
                "LOCAL_DIRECT_DISABLED_FORWARDER_NOT_READY | ${policyDecision.detail}"
            )
            if (gatewayUsable && trustHealthy) {
                VpnLogManager.info(
                    "VPN_ROUTE_MESH_GATEWAY_SELECTED",
                    "Fallback ke MESH_GATEWAY karena LOCAL_DIRECT belum siap | gateway=${gateway.gatewayName}"
                )
                return AccessDecision(
                    allowed = true,
                    gatewayAvailable = true,
                    trustHealthy = true,
                    participantValid = true,
                    walletBalance = policyDecision.walletBalance,
                    gatewayAddress = gateway.gatewayAddress,
                    gatewayId = gateway.gatewayId,
                    routeScore = gateway.routeScore,
                    userStatus = policyDecision.userTier.name,
                    gatewayName = gateway.gatewayName,
                    forwardMode = MeshForwardMode.MESH_GATEWAY,
                    detail = "MESH_GATEWAY | ACCESS_ALLOWED | LOCAL_DIRECT_DISABLED_FORWARDER_NOT_READY"
                )
            }

            VpnLogManager.warn(
                "VPN_ROUTE_BLOCKED_NO_VALID_GATEWAY",
                "NO_USABLE_FORWARD_PATH | LOCAL_DIRECT disabled dan gateway tidak punya address valid/kurang sehat."
            )
            return AccessDecision(
                allowed = false,
                gatewayAvailable = false,
                trustHealthy = false,
                participantValid = true,
                walletBalance = policyDecision.walletBalance,
                gatewayAddress = "",
                gatewayId = "",
                routeScore = gateway.routeScore,
                userStatus = policyDecision.userTier.name,
                gatewayName = "",
                forwardMode = MeshForwardMode.BLOCKED,
                detail = "NO_USABLE_FORWARD_PATH | LOCAL_DIRECT_DISABLED_FORWARDER_NOT_READY | NO_VALID_GATEWAY"
            )
        }

        if (!gatewayUsable) {
            VpnLogManager.warn(
                "VPN_ROUTE_BLOCKED_NO_VALID_GATEWAY",
                "GATEWAY_NOT_AVAILABLE | gateway address kosong atau node belum siap."
            )
            return AccessDecision(
                allowed = false,
                gatewayAvailable = false,
                trustHealthy = false,
                participantValid = policyDecision.allowed,
                walletBalance = policyDecision.walletBalance,
                gatewayAddress = "",
                gatewayId = "",
                routeScore = gateway.routeScore,
                userStatus = policyDecision.userTier.name,
                gatewayName = "",
                forwardMode = MeshForwardMode.BLOCKED,
                detail = "GATEWAY_NOT_AVAILABLE | ${policyDecision.detail}"
            )
        }

        if (!trustHealthy) {
            return AccessDecision(
                allowed = false,
                gatewayAvailable = true,
                trustHealthy = false,
                participantValid = policyDecision.allowed,
                walletBalance = policyDecision.walletBalance,
                gatewayAddress = gateway.gatewayAddress,
                gatewayId = gateway.gatewayId,
                routeScore = gateway.routeScore,
                userStatus = policyDecision.userTier.name,
                gatewayName = gateway.gatewayName,
                forwardMode = MeshForwardMode.BLOCKED,
                detail = "GATEWAY_TRUST_TOO_LOW | skor ${gateway.routeScore}/100"
            )
        }

        val forwardMode =
            when {
                wantsLocalDirect && localDirectReady -> MeshForwardMode.LOCAL_DIRECT
                wantsMeshGateway -> MeshForwardMode.MESH_GATEWAY
                else -> MeshForwardMode.BLOCKED
            }

        if (forwardMode == MeshForwardMode.MESH_GATEWAY) {
            VpnLogManager.info(
                "VPN_ROUTE_MESH_GATEWAY_SELECTED",
                "gateway=${gateway.gatewayName}@${gateway.gatewayAddress}:${gateway.gatewayPort} score=${gateway.routeScore} | ${policyDecision.detail}"
            )
        } else if (forwardMode == MeshForwardMode.BLOCKED) {
            VpnLogManager.warn(
                "VPN_ROUTE_BLOCKED_NO_FORWARDER",
                "NO_USABLE_FORWARD_PATH | routeMode=${policyDecision.routeMode.name}"
            )
        }

        return AccessDecision(
            allowed = forwardMode != MeshForwardMode.BLOCKED,
            gatewayAvailable = true,
            trustHealthy = true,
            participantValid = true,
            walletBalance = policyDecision.walletBalance,
            gatewayAddress = gateway.gatewayAddress,
            gatewayId = gateway.gatewayId,
            routeScore = gateway.routeScore,
            userStatus = policyDecision.userTier.name,
            gatewayName = gateway.gatewayName,
            forwardMode = forwardMode,
            detail =
                if (forwardMode == MeshForwardMode.BLOCKED) {
                    "NO_USABLE_FORWARD_PATH | ${policyDecision.detail}"
                } else {
                    "${forwardMode.name} | ACCESS_ALLOWED | ${policyDecision.detail}"
                }
        )
    }
}
