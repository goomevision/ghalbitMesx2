package com.ghalbitnet.meshx2.economy

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object CommunityRelayEconomy {
    private const val TAG = "GHALBIT-REWARD"
    private const val PREFS = "ghalbit_relay_economy"
    private const val KEY_CONTRIBUTIONS = "relay_contributions"
    private const val KEY_REWARDS = "relay_pending_rewards"

    fun recordContribution(
        context: Context,
        nodeId: String,
        relayedPackets: Int,
        uptimeScore: Int,
        bandwidthScore: Int
    ) {
        val contributions = getContributions(context).associateBy { it.nodeId }.toMutableMap()
        contributions[nodeId] = RelayContribution(
            nodeId = nodeId,
            relayedPacketCount = relayedPackets,
            uptimeScore = uptimeScore,
            bandwidthScore = bandwidthScore
        )
        saveContributions(context, contributions.values.toList())

        val pendingAmount = ((relayedPackets * 0.05) + (uptimeScore * 0.01) + (bandwidthScore * 0.01))
        upsertPendingReward(context, RewardPending(nodeId, pendingAmount, "LOCAL_RELAY_ACCOUNTING"))
        Log.d(TAG, "Updated relay contribution for $nodeId reward=$pendingAmount")
    }

    fun getContributions(context: Context): List<RelayContribution> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CONTRIBUTIONS, "[]").orEmpty()
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    RelayContribution(
                        nodeId = item.getString("nodeId"),
                        relayedPacketCount = item.optInt("relayedPacketCount", 0),
                        uptimeScore = item.optInt("uptimeScore", 0),
                        bandwidthScore = item.optInt("bandwidthScore", 0),
                        updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    fun getPendingRewards(context: Context): List<RewardPending> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_REWARDS, "[]").orEmpty()
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    RewardPending(
                        nodeId = item.getString("nodeId"),
                        amount = item.optDouble("amount", 0.0),
                        reason = item.optString("reason", "LOCAL_RELAY_ACCOUNTING"),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    private fun upsertPendingReward(context: Context, reward: RewardPending) {
        val rewards = getPendingRewards(context).associateBy { it.nodeId }.toMutableMap()
        rewards[reward.nodeId] = reward
        val array = JSONArray()
        rewards.values.forEach { pending ->
            array.put(
                JSONObject()
                    .put("nodeId", pending.nodeId)
                    .put("amount", pending.amount)
                    .put("reason", pending.reason)
                    .put("createdAt", pending.createdAt)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_REWARDS, array.toString()).apply()
    }

    private fun saveContributions(context: Context, contributions: List<RelayContribution>) {
        val array = JSONArray()
        contributions.forEach { contribution ->
            array.put(
                JSONObject()
                    .put("nodeId", contribution.nodeId)
                    .put("relayedPacketCount", contribution.relayedPacketCount)
                    .put("uptimeScore", contribution.uptimeScore)
                    .put("bandwidthScore", contribution.bandwidthScore)
                    .put("updatedAt", contribution.updatedAt)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_CONTRIBUTIONS, array.toString()).apply()
    }
}
