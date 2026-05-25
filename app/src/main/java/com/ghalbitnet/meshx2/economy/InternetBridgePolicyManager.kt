package com.ghalbitnet.meshx2.economy

import android.content.Context
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.chat.GlobalContactDirectory
import com.ghalbitnet.meshx2.core.network.ConnectivityStatusDetector
import com.ghalbitnet.meshx2.core.network.InternetGatewayRegistry
import com.ghalbitnet.meshx2.core.server.FirebaseRemoteSyncManager
import com.ghalbitnet.meshx2.core.server.MeshServerApiClient
import com.ghalbitnet.meshx2.discovery.DiscoveryManager
import com.ghalbitnet.meshx2.security.KeyStoreManager
import com.ghalbitnet.meshx2.settings.OnboardingManager
import com.ghalbitnet.meshx2.token.TokenManager
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

object InternetBridgePolicyManager {

    enum class UserTier {
        STANDARD,
        PRIORITY,
        BLOCKED
    }

    enum class RouteMode {
        LOCAL_DIRECT,
        REMOTE_GATEWAY,
        UNAVAILABLE
    }

    data class Policy(
        val versionLabel: String,
        val maxSessionMinutes: Int,
        val maxSessionMb: Int,
        val standardDailyQuotaMb: Int,
        val priorityDailyQuotaMb: Int,
        val minimumInternetAccessBalance: Double,
        val allowLocalGateway: Boolean,
        val allowRemoteGateway: Boolean,
        val priorityUsers: Set<String>,
        val blockedUsers: Set<String>
    )

    data class Decision(
        val allowed: Boolean,
        val userTier: UserTier,
        val routeMode: RouteMode,
        val gatewayId: String,
        val gatewayName: String,
        val gatewayAddress: String,
        val gatewayScore: Int,
        val routeScore: Int,
        val relayPath: List<ServiceParticipant>,
        val walletBalance: Double,
        val dailyUsedMb: Double,
        val dailyQuotaMb: Int,
        val detail: String
    )

    fun current(
        context: Context
    ): Policy {
        val cached =
            FirebaseRemoteSyncManager.cachedBridgePolicyJson(context)
                .ifBlank { MeshServerApiClient.cachedBridgePolicyJson(context) }
        if (cached.isNotBlank()) {
            parseServerPolicy(cached)?.let { return it }
        }

        return defaultPolicy()
    }

    private fun defaultPolicy(): Policy {
        return Policy(
            versionLabel = "BRIDGE-2026.05",
            maxSessionMinutes = 45,
            maxSessionMb = 512,
            standardDailyQuotaMb = 1024,
            priorityDailyQuotaMb = 4096,
            minimumInternetAccessBalance = 5.0,
            allowLocalGateway = true,
            allowRemoteGateway = true,
            priorityUsers = emptySet(),
            blockedUsers = emptySet()
        )
    }

    private fun parseServerPolicy(rawJson: String): Policy? {
        return runCatching {
            val root = JSONObject(rawJson)
            val fallback = defaultPolicy()
            Policy(
                versionLabel = root.optString("versionLabel", fallback.versionLabel),
                maxSessionMinutes = root.optInt("maxSessionMinutes", fallback.maxSessionMinutes),
                maxSessionMb = root.optInt("maxSessionMb", fallback.maxSessionMb),
                standardDailyQuotaMb = root.optInt("standardDailyQuotaMb", fallback.standardDailyQuotaMb),
                priorityDailyQuotaMb = root.optInt("priorityDailyQuotaMb", fallback.priorityDailyQuotaMb),
                minimumInternetAccessBalance = root.optDouble("minimumInternetAccessBalance", fallback.minimumInternetAccessBalance),
                allowLocalGateway = root.optBoolean("allowLocalGateway", fallback.allowLocalGateway),
                allowRemoteGateway = root.optBoolean("allowRemoteGateway", fallback.allowRemoteGateway),
                priorityUsers = parseStringSet(root.optJSONArray("priorityUsers")),
                blockedUsers = parseStringSet(root.optJSONArray("blockedUsers"))
            )
        }.getOrNull()
    }

    private fun parseStringSet(source: JSONArray?): Set<String> {
        if (source == null) return emptySet()
        return buildSet {
            for (i in 0 until source.length()) {
                val value = source.optString(i).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }

    fun evaluate(
        context: Context,
        userGlobalId: String = ""
    ): Decision {
        val policy = current(context)
        if (userGlobalId.isBlank()) {
            return Decision(
                allowed = false,
                userTier = UserTier.STANDARD,
                routeMode = RouteMode.UNAVAILABLE,
                gatewayId = "",
                gatewayName = "",
                gatewayAddress = "",
                gatewayScore = 0,
                routeScore = 0,
                relayPath = emptyList(),
                walletBalance = 0.0,
                dailyUsedMb = 0.0,
                dailyQuotaMb = policy.standardDailyQuotaMb,
                detail = context.getString(R.string.internet_bridge_policy_identity_missing)
            )
        }

        val localGlobalId =
            runCatching {
                com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager.buildGlobalId(
                    KeyStoreManager(context).publicKeyBase64
                )
            }.getOrDefault("")
        val localRequest = userGlobalId == localGlobalId
        val localOnboarding = if (localRequest) OnboardingManager.snapshot(context) else null
        val cachedParticipant = FirebaseRemoteSyncManager.cachedParticipantProfile(context, userGlobalId)

        val dailyUsedMb =
            MeshServiceLedger.dailyBridgeUsageMb(context, userGlobalId)
        val cachedWalletBalance = FirebaseRemoteSyncManager.cachedWalletBalance(context, userGlobalId)
        val walletBalance =
            cachedWalletBalance ?: if (localRequest) {
                TokenManager.init(context)
                runBlocking {
                    TokenManager.ensureWalletBootstrap(userGlobalId)
                    TokenManager.getWalletBalanceForGlobalId(userGlobalId)
                }
            } else {
                0.0
            }

        val peerPolicy =
            run {
                val cleanId = GlobalContactDirectory.normalizeGlobalId(userGlobalId)
                FirebaseRemoteSyncManager.cachedPeerPolicy(context, cleanId)?.let { cached ->
                    InternetBridgePeerPolicyManager.PeerPolicy(
                        globalId = cleanId,
                        tier =
                            runCatching {
                                UserTier.valueOf(cached.tier)
                            }.getOrDefault(UserTier.STANDARD),
                        customDailyQuotaMb = cached.customDailyQuotaMb
                    )
                } ?: InternetBridgePeerPolicyManager.getPolicy(context, cleanId)
            }

        val participantKnown =
            if (localRequest) {
                localOnboarding?.completed == true
            } else {
                cachedParticipant != null
            }
        val contributionApproved =
            if (localRequest) {
                localOnboarding?.contributionApproved == true
            } else {
                cachedParticipant?.contributionApproved == true
            }
        val participantOnline =
            if (localRequest) {
                true
            } else {
                cachedParticipant?.online == true
            }

        if (!participantKnown) {
            return Decision(
                allowed = false,
                userTier = UserTier.STANDARD,
                routeMode = RouteMode.UNAVAILABLE,
                gatewayId = "",
                gatewayName = "",
                gatewayAddress = "",
                gatewayScore = 0,
                routeScore = 0,
                relayPath = emptyList(),
                walletBalance = walletBalance,
                dailyUsedMb = dailyUsedMb,
                dailyQuotaMb = policy.standardDailyQuotaMb,
                detail = context.getString(R.string.internet_bridge_policy_participant_unknown)
            )
        }

        if (!contributionApproved) {
            return Decision(
                allowed = false,
                userTier = UserTier.STANDARD,
                routeMode = RouteMode.UNAVAILABLE,
                gatewayId = "",
                gatewayName = "",
                gatewayAddress = "",
                gatewayScore = 0,
                routeScore = 0,
                relayPath = emptyList(),
                walletBalance = walletBalance,
                dailyUsedMb = dailyUsedMb,
                dailyQuotaMb = policy.standardDailyQuotaMb,
                detail = context.getString(R.string.internet_bridge_policy_contribution_required)
            )
        }

        if (!participantOnline) {
            return Decision(
                allowed = false,
                userTier = UserTier.STANDARD,
                routeMode = RouteMode.UNAVAILABLE,
                gatewayId = "",
                gatewayName = "",
                gatewayAddress = "",
                gatewayScore = 0,
                routeScore = 0,
                relayPath = emptyList(),
                walletBalance = walletBalance,
                dailyUsedMb = dailyUsedMb,
                dailyQuotaMb = policy.standardDailyQuotaMb,
                detail = context.getString(R.string.internet_bridge_policy_participant_offline)
            )
        }

        if (!localRequest && cachedWalletBalance == null) {
            return Decision(
                allowed = false,
                userTier = UserTier.STANDARD,
                routeMode = RouteMode.UNAVAILABLE,
                gatewayId = "",
                gatewayName = "",
                gatewayAddress = "",
                gatewayScore = 0,
                routeScore = 0,
                relayPath = emptyList(),
                walletBalance = 0.0,
                dailyUsedMb = dailyUsedMb,
                dailyQuotaMb = policy.standardDailyQuotaMb,
                detail = context.getString(R.string.internet_bridge_policy_wallet_unknown)
            )
        }

        val userTier =
            when {
                peerPolicy != null -> peerPolicy.tier
                userGlobalId.isNotBlank() && policy.blockedUsers.contains(userGlobalId) -> UserTier.BLOCKED
                userGlobalId.isNotBlank() && policy.priorityUsers.contains(userGlobalId) -> UserTier.PRIORITY
                else -> UserTier.STANDARD
            }

        val dailyQuotaMb =
            peerPolicy?.customDailyQuotaMb ?: when (userTier) {
                UserTier.PRIORITY -> policy.priorityDailyQuotaMb
                UserTier.STANDARD, UserTier.BLOCKED -> policy.standardDailyQuotaMb
            }

        if (userTier == UserTier.BLOCKED) {
            return Decision(
                allowed = false,
                userTier = userTier,
                routeMode = RouteMode.UNAVAILABLE,
                gatewayId = "",
                gatewayName = "",
                gatewayAddress = "",
                gatewayScore = 0,
                routeScore = 0,
                relayPath = emptyList(),
                walletBalance = walletBalance,
                dailyUsedMb = dailyUsedMb,
                dailyQuotaMb = dailyQuotaMb,
                detail = context.getString(R.string.internet_bridge_policy_user_blocked)
            )
        }

        if (userGlobalId.isNotBlank() && walletBalance < policy.minimumInternetAccessBalance) {
            return Decision(
                allowed = false,
                userTier = userTier,
                routeMode = RouteMode.UNAVAILABLE,
                gatewayId = "",
                gatewayName = "",
                gatewayAddress = "",
                gatewayScore = 0,
                routeScore = 0,
                relayPath = emptyList(),
                walletBalance = walletBalance,
                dailyUsedMb = dailyUsedMb,
                dailyQuotaMb = dailyQuotaMb,
                detail = context.getString(
                    R.string.internet_bridge_policy_low_balance,
                    policy.minimumInternetAccessBalance,
                    walletBalance
                )
            )
        }

        if (dailyUsedMb >= dailyQuotaMb) {
            return Decision(
                allowed = false,
                userTier = userTier,
                routeMode = RouteMode.UNAVAILABLE,
                gatewayId = "",
                gatewayName = "",
                gatewayAddress = "",
                gatewayScore = 0,
                routeScore = 0,
                relayPath = emptyList(),
                walletBalance = walletBalance,
                dailyUsedMb = dailyUsedMb,
                dailyQuotaMb = dailyQuotaMb,
                detail = context.getString(R.string.internet_bridge_policy_daily_limit, dailyQuotaMb)
            )
        }

        val nodes = DiscoveryManager.discoverNodes()
        val gatewayCandidates =
            InternetGatewayRegistry.candidates(context, nodes)
        val selectedRoute =
            InternetRouteCooperationManager.choosePlan(context, nodes)
        val selectedGateway =
            selectedRoute?.gateway ?: gatewayCandidates.firstOrNull()
        val backupSummary =
            InternetRoutePlanner.backupSummary(context, nodes, selectedRoute)

        if (selectedGateway?.isLocal == true) {
            return if (policy.allowLocalGateway) {
                Decision(
                    allowed = true,
                    userTier = userTier,
                    routeMode = RouteMode.LOCAL_DIRECT,
                    gatewayId = selectedGateway.nodeId,
                    gatewayName = selectedGateway.name,
                    gatewayAddress = selectedGateway.ipAddress,
                    gatewayScore = selectedGateway.routeScore,
                    routeScore = selectedRoute?.routeScore ?: selectedGateway.routeScore,
                    relayPath = selectedRoute?.relayPath.orEmpty(),
                    walletBalance = walletBalance,
                    dailyUsedMb = dailyUsedMb,
                    dailyQuotaMb = dailyQuotaMb,
                    detail =
                        context.getString(R.string.internet_bridge_policy_local_ready) +
                            " Skor jalur ${(selectedRoute?.routeScore ?: selectedGateway.routeScore)}/100 (${selectedRoute?.routeReason ?: selectedGateway.routeReason})." +
                            if (backupSummary.isBlank()) "" else " Cadangan: $backupSummary."
                )
            } else {
                Decision(
                    allowed = false,
                    userTier = userTier,
                    routeMode = RouteMode.LOCAL_DIRECT,
                    gatewayId = selectedGateway.nodeId,
                    gatewayName = selectedGateway.name,
                    gatewayAddress = selectedGateway.ipAddress,
                    gatewayScore = selectedGateway.routeScore,
                    routeScore = selectedRoute?.routeScore ?: selectedGateway.routeScore,
                    relayPath = selectedRoute?.relayPath.orEmpty(),
                    walletBalance = walletBalance,
                    dailyUsedMb = dailyUsedMb,
                    dailyQuotaMb = dailyQuotaMb,
                    detail = context.getString(R.string.internet_bridge_policy_local_blocked)
                )
            }
        }

        if (selectedGateway != null) {
            return if (policy.allowRemoteGateway) {
                Decision(
                    allowed = true,
                    userTier = userTier,
                    routeMode = RouteMode.REMOTE_GATEWAY,
                    gatewayId = selectedGateway.nodeId,
                    gatewayName = selectedGateway.name,
                    gatewayAddress = selectedGateway.ipAddress,
                    gatewayScore = selectedGateway.routeScore,
                    routeScore = selectedRoute?.routeScore ?: selectedGateway.routeScore,
                    relayPath = selectedRoute?.relayPath.orEmpty(),
                    walletBalance = walletBalance,
                    dailyUsedMb = dailyUsedMb,
                    dailyQuotaMb = dailyQuotaMb,
                    detail =
                        context.getString(
                            R.string.internet_bridge_policy_remote_ready,
                            selectedGateway.name
                        ) + " Skor jalur ${(selectedRoute?.routeScore ?: selectedGateway.routeScore)}/100 (${selectedRoute?.routeReason ?: selectedGateway.routeReason})." +
                            if (backupSummary.isBlank()) "" else " Cadangan: $backupSummary."
                )
            } else {
                Decision(
                    allowed = false,
                    userTier = userTier,
                    routeMode = RouteMode.REMOTE_GATEWAY,
                    gatewayId = selectedGateway.nodeId,
                    gatewayName = selectedGateway.name,
                    gatewayAddress = selectedGateway.ipAddress,
                    gatewayScore = selectedGateway.routeScore,
                    routeScore = selectedRoute?.routeScore ?: selectedGateway.routeScore,
                    relayPath = selectedRoute?.relayPath.orEmpty(),
                    walletBalance = walletBalance,
                    dailyUsedMb = dailyUsedMb,
                    dailyQuotaMb = dailyQuotaMb,
                    detail = context.getString(R.string.internet_bridge_policy_remote_blocked)
                )
            }
        }

        return Decision(
            allowed = false,
            userTier = userTier,
            routeMode = RouteMode.UNAVAILABLE,
            gatewayId = "",
            gatewayName = "",
            gatewayAddress = "",
            gatewayScore = 0,
            routeScore = 0,
            relayPath = emptyList(),
            walletBalance = walletBalance,
            dailyUsedMb = dailyUsedMb,
            dailyQuotaMb = dailyQuotaMb,
            detail = context.getString(R.string.internet_bridge_policy_waiting_gateway)
        )
    }
}
