package com.ghalbitnet.meshx2.economy

import android.content.Context
import com.ghalbitnet.meshx2.chat.GlobalContactDirectory

object InternetBridgePeerPolicyManager {

    data class PeerPolicy(
        val globalId: String,
        val tier: InternetBridgePolicyManager.UserTier,
        val customDailyQuotaMb: Int?
    )

    data class Summary(
        val standardCount: Int,
        val priorityCount: Int,
        val blockedCount: Int
    )

    private const val PREFS_NAME = "internet_bridge_peer_policy"
    private const val KEY_TIER_PREFIX = "tier_"
    private const val KEY_QUOTA_PREFIX = "quota_"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPolicy(
        context: Context,
        globalId: String
    ): PeerPolicy {
        val cleanId = GlobalContactDirectory.normalizeGlobalId(globalId)
        val rawTier =
            prefs(context).getString(
                "$KEY_TIER_PREFIX$cleanId",
                InternetBridgePolicyManager.UserTier.STANDARD.name
            ).orEmpty()

        val quota =
            if (prefs(context).contains("$KEY_QUOTA_PREFIX$cleanId")) {
                prefs(context).getInt("$KEY_QUOTA_PREFIX$cleanId", 0).takeIf { it > 0 }
            } else {
                null
            }

        return PeerPolicy(
            globalId = cleanId,
            tier = InternetBridgePolicyManager.UserTier.valueOf(rawTier),
            customDailyQuotaMb = quota
        )
    }

    fun savePolicy(
        context: Context,
        policy: PeerPolicy
    ) {
        val cleanId = GlobalContactDirectory.normalizeGlobalId(policy.globalId)
        prefs(context)
            .edit()
            .putString("$KEY_TIER_PREFIX$cleanId", policy.tier.name)
            .apply {
                if (policy.customDailyQuotaMb != null && policy.customDailyQuotaMb > 0) {
                    putInt("$KEY_QUOTA_PREFIX$cleanId", policy.customDailyQuotaMb)
                } else {
                    remove("$KEY_QUOTA_PREFIX$cleanId")
                }
            }
            .apply()
    }

    fun cycleTier(
        context: Context,
        globalId: String
    ): PeerPolicy {
        val current = getPolicy(context, globalId)
        val nextTier =
            when (current.tier) {
                InternetBridgePolicyManager.UserTier.STANDARD -> InternetBridgePolicyManager.UserTier.PRIORITY
                InternetBridgePolicyManager.UserTier.PRIORITY -> InternetBridgePolicyManager.UserTier.BLOCKED
                InternetBridgePolicyManager.UserTier.BLOCKED -> InternetBridgePolicyManager.UserTier.STANDARD
            }

        return current.copy(tier = nextTier).also { savePolicy(context, it) }
    }

    fun allPolicies(
        context: Context
    ): List<PeerPolicy> {
        return GlobalContactDirectory.getAll(context).map { contact ->
            getPolicy(context, contact.globalId)
        }
    }

    fun summary(
        context: Context
    ): Summary {
        val all = allPolicies(context)
        return Summary(
            standardCount = all.count { it.tier == InternetBridgePolicyManager.UserTier.STANDARD },
            priorityCount = all.count { it.tier == InternetBridgePolicyManager.UserTier.PRIORITY },
            blockedCount = all.count { it.tier == InternetBridgePolicyManager.UserTier.BLOCKED }
        )
    }
}
