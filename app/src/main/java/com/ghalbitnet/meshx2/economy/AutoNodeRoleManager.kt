package com.ghalbitnet.meshx2.economy

import android.content.Context
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.core.network.ConnectivityScopeDetector
import com.ghalbitnet.meshx2.core.network.InternetProviderReadinessManager
import com.ghalbitnet.meshx2.core.runtime.MeshRuntimeState
import kotlin.math.roundToInt

object AutoNodeRoleManager {

    enum class NetworkRole {
        CLIENT,
        GATEWAY,
        RELAY,
        VALIDATOR,
        MINER
    }

    data class RoleSummary(
        val roles: List<NetworkRole>,
        val trustScore: Int,
        val contributionScore: Int,
        val title: String,
        val detail: String
    )

    fun currentDevice(
        context: Context,
        economySnapshot: ServiceEconomySnapshot,
        localWalletRoleName: String
    ): RoleSummary {
        val scope = ConnectivityScopeDetector.detect(context)
        val runtime = MeshRuntimeState.snapshot()
        val hasMeshPeers = runtime.nodeCount > 0
        val providerReadiness = InternetProviderReadinessManager.snapshot(context)
        val hasGatewayReward = economySnapshot.totalGatewayReward > 0.0
        val hasRelayReward = economySnapshot.totalRelayReward > 0.0
        val hasValidatorReward = economySnapshot.totalValidatorReward > 0.0
        val hasContributionReward =
            hasGatewayReward || hasRelayReward || hasValidatorReward || economySnapshot.totalBuilderReward > 0.0

        val roles = mutableListOf(NetworkRole.CLIENT)
        if (providerReadiness.providerReady || hasGatewayReward || providerReadiness.providerActive) {
            roles += NetworkRole.GATEWAY
        }
        if (hasMeshPeers || hasRelayReward) {
            roles += NetworkRole.RELAY
        }
        if (runtime.isMeshRunning && (hasValidatorReward || economySnapshot.sessionCount > 0)) {
            roles += NetworkRole.VALIDATOR
        }
        if (hasContributionReward || (roles.contains(NetworkRole.RELAY) && runtime.isMeshRunning)) {
            roles += NetworkRole.MINER
        }

        val trustBase = when {
            roles.contains(NetworkRole.GATEWAY) && roles.contains(NetworkRole.RELAY) -> 72
            roles.contains(NetworkRole.GATEWAY) -> 66
            roles.contains(NetworkRole.RELAY) -> 60
            else -> 52
        }
        val trustBoost =
            (economySnapshot.sessionCount.coerceAtMost(10) * 2) +
                if (hasValidatorReward) 8 else 0 +
                if (localWalletRoleName.equals(EconomyRoleManager.Role.BUILDER.name, true)) 6 else 0
        val trustScore = (trustBase + trustBoost).coerceIn(0, 100)

        val contributionRaw =
            economySnapshot.totalGatewayReward +
                economySnapshot.totalRelayReward +
                economySnapshot.totalValidatorReward +
                economySnapshot.totalBuilderReward +
                (economySnapshot.totalBytes / 1024.0 / 1024.0 / 16.0)
        val contributionScore = contributionRaw.coerceIn(0.0, 100.0).roundToInt()

        val title = roles.joinToString(" • ") { displayName(context, it) }
        val detail =
            context.getString(
                R.string.auto_role_current_detail,
                scope.scope.label,
                runtime.nodeCount,
                economySnapshot.sessionCount,
                trustScore,
                contributionScore
            )

        return RoleSummary(
            roles = roles.distinct(),
            trustScore = trustScore,
            contributionScore = contributionScore,
            title = title,
            detail = detail
        )
    }

    fun peer(
        context: Context,
        economySnapshot: PeerServiceSnapshot,
        reputation: PeerReputationManager.Reputation,
        routeAllowed: Boolean
    ): RoleSummary {
        val roles = mutableListOf(NetworkRole.CLIENT)
        if (economySnapshot.totalGatewayReward > 0.0) {
            roles += NetworkRole.GATEWAY
        }
        if (economySnapshot.totalRelayReward > 0.0 || economySnapshot.sessionCount > 0) {
            roles += NetworkRole.RELAY
        }
        if (economySnapshot.totalValidatorReward > 0.0) {
            roles += NetworkRole.VALIDATOR
        }
        if (
            economySnapshot.totalGatewayReward +
                economySnapshot.totalRelayReward +
                economySnapshot.totalValidatorReward > 0.0
        ) {
            roles += NetworkRole.MINER
        }

        val title = roles.distinct().joinToString(" • ") { displayName(context, it) }
        val detail =
            context.getString(
                R.string.auto_role_peer_detail,
                economySnapshot.totalMegaBytes,
                economySnapshot.sessionCount,
                reputation.score,
                if (routeAllowed) {
                    context.getString(R.string.auto_role_route_allowed)
                } else {
                    context.getString(R.string.auto_role_route_waiting)
                }
            )

        return RoleSummary(
            roles = roles.distinct(),
            trustScore = reputation.score.coerceIn(0, 100),
            contributionScore = (
                economySnapshot.totalGatewayReward +
                    economySnapshot.totalRelayReward +
                    economySnapshot.totalValidatorReward +
                    (economySnapshot.totalMegaBytes / 8.0)
                ).coerceIn(0.0, 100.0).roundToInt(),
            title = title,
            detail = detail
        )
    }

    fun displayName(
        context: Context,
        role: NetworkRole
    ): String {
        return when (role) {
            NetworkRole.CLIENT -> context.getString(R.string.auto_role_client)
            NetworkRole.GATEWAY -> context.getString(R.string.auto_role_gateway)
            NetworkRole.RELAY -> context.getString(R.string.auto_role_relay)
            NetworkRole.VALIDATOR -> context.getString(R.string.auto_role_validator)
            NetworkRole.MINER -> context.getString(R.string.auto_role_miner)
        }
    }
}
