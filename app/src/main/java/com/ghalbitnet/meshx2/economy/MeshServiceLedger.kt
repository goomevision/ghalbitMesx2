package com.ghalbitnet.meshx2.economy

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object MeshServiceLedger {

    private const val PREFS_NAME = "mesh_service_ledger"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 80

    fun record(
        context: Context,
        entry: ServiceLedgerEntry
    ) {
        val prefs =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val current =
            JSONArray(prefs.getString(KEY_ENTRIES, "[]"))

        current.put(serializeEntry(entry))

        val trimmed =
            JSONArray().apply {
                val start = maxOf(0, current.length() - MAX_ENTRIES)
                for (index in start until current.length()) {
                    put(current.getJSONObject(index))
                }
            }

        prefs.edit()
            .putString(KEY_ENTRIES, trimmed.toString())
            .apply()
    }

    fun recentEntries(
        context: Context,
        limit: Int = 10
    ): List<ServiceLedgerEntry> {
        val prefs =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val array =
            JSONArray(prefs.getString(KEY_ENTRIES, "[]"))

        val items = mutableListOf<ServiceLedgerEntry>()

        for (index in array.length() - 1 downTo 0) {
            items += deserializeEntry(array.getJSONObject(index))
            if (items.size >= limit) {
                break
            }
        }

        return items
    }

    fun snapshot(
        context: Context
    ): ServiceEconomySnapshot {
        val entries =
            recentEntries(context, MAX_ENTRIES)

        val totalBytes =
            entries.sumOf { it.session.totalBytes }

        val totalBurned =
            entries.sumOf { it.settlement.burnAmount }

        val totalGatewayReward =
            entries.sumOf { it.settlement.gatewayReward }

        val totalRelayReward =
            entries.sumOf { it.settlement.totalRelayReward }

        val totalTreasury =
            entries.sumOf { it.settlement.treasuryReserve }

        val totalBuilderReward =
            entries.sumOf { it.settlement.builderReward }

        val lastUpdatedAt =
            entries.maxOfOrNull { it.session.endedAt } ?: 0L

        val latestSummary =
            entries.firstOrNull()?.settlement?.notes
                ?: "Belum ada sesi jasa yang tercatat."

        return ServiceEconomySnapshot(
            sessionCount = entries.size,
            totalBytes = totalBytes,
            totalBurned = totalBurned,
            totalGatewayReward = totalGatewayReward,
            totalRelayReward = totalRelayReward,
            totalBuilderReward = totalBuilderReward,
            totalTreasury = totalTreasury,
            lastUpdatedAt = lastUpdatedAt,
            latestSummary = latestSummary
        )
    }

    fun clear(
        context: Context
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ENTRIES)
            .apply()
    }

    private fun serializeEntry(
        entry: ServiceLedgerEntry
    ): JSONObject {
        val relayArray =
            JSONArray().apply {
                entry.session.relayPath.forEach { relay ->
                    put(
                        JSONObject()
                            .put("nodeId", relay.nodeId)
                            .put("nodeName", relay.nodeName)
                            .put("nodeAddress", relay.nodeAddress)
                            .put("role", relay.role.name)
                            .put("local", relay.local)
                            .put("trustScore", relay.trustScore)
                    )
                }
            }

        val relayRewardArray =
            JSONArray().apply {
                entry.settlement.relayRewards.forEach { reward ->
                    put(
                        JSONObject()
                            .put("nodeId", reward.nodeId)
                            .put("nodeName", reward.nodeName)
                            .put("nodeAddress", reward.nodeAddress)
                            .put("local", reward.local)
                            .put("amount", reward.amount)
                    )
                }
            }

        val session =
            entry.session

        val settlement =
            entry.settlement

        return JSONObject()
            .put(
                "session",
                JSONObject()
                    .put("sessionId", session.sessionId)
                    .put("serviceFamily", session.serviceFamily.name)
                    .put("userGlobalId", session.userGlobalId)
                    .put("bytesUp", session.bytesUp)
                    .put("bytesDown", session.bytesDown)
                    .put("durationMs", session.durationMs)
                    .put("startedAt", session.startedAt)
                    .put("endedAt", session.endedAt)
                    .put("success", session.success)
                    .put("averageLatencyMs", session.averageLatencyMs)
                    .put("localInternetProvider", session.localInternetProvider)
                    .put("gatewayNodeId", session.gatewayNodeId)
                    .put("gatewayNodeName", session.gatewayNodeName)
                    .put("gatewayNodeAddress", session.gatewayNodeAddress)
                    .put("relayPath", relayArray)
            )
            .put(
                "settlement",
                JSONObject()
                    .put("sessionId", settlement.sessionId)
                    .put("validMegaBytes", settlement.validMegaBytes)
                    .put("burnAmount", settlement.burnAmount)
                    .put("gatewayReward", settlement.gatewayReward)
                    .put("builderReward", settlement.builderReward)
                    .put("treasuryReserve", settlement.treasuryReserve)
                    .put("validationScore", settlement.validationScore)
                    .put("notes", settlement.notes)
                    .put("relayRewards", relayRewardArray)
            )
    }

    private fun deserializeEntry(
        source: JSONObject
    ): ServiceLedgerEntry {
        val sessionJson =
            source.getJSONObject("session")

        val relayPathJson =
            sessionJson.getJSONArray("relayPath")

        val relayPath =
            buildList {
                for (index in 0 until relayPathJson.length()) {
                    val relay = relayPathJson.getJSONObject(index)
                    add(
                        ServiceParticipant(
                            nodeId = relay.optString("nodeId"),
                            nodeName = relay.optString("nodeName"),
                            nodeAddress = relay.optString("nodeAddress"),
                            role = ServiceRole.valueOf(relay.optString("role", ServiceRole.RELAY.name)),
                            local = relay.optBoolean("local"),
                            trustScore = relay.optInt("trustScore", 50)
                        )
                    )
                }
            }

        val settlementJson =
            source.getJSONObject("settlement")

        val relayRewardsJson =
            settlementJson.getJSONArray("relayRewards")

        val relayRewards =
            buildList {
                for (index in 0 until relayRewardsJson.length()) {
                    val reward = relayRewardsJson.getJSONObject(index)
                    add(
                        ParticipantReward(
                            nodeId = reward.optString("nodeId"),
                            nodeName = reward.optString("nodeName"),
                            nodeAddress = reward.optString("nodeAddress"),
                            local = reward.optBoolean("local"),
                            amount = reward.optDouble("amount")
                        )
                    )
                }
            }

        return ServiceLedgerEntry(
            session = ServiceSessionRecord(
                sessionId = sessionJson.optString("sessionId"),
                serviceFamily = ServiceFamily.valueOf(sessionJson.optString("serviceFamily", ServiceFamily.OTHER.name)),
                userGlobalId = sessionJson.optString("userGlobalId"),
                bytesUp = sessionJson.optLong("bytesUp"),
                bytesDown = sessionJson.optLong("bytesDown"),
                durationMs = sessionJson.optLong("durationMs"),
                startedAt = sessionJson.optLong("startedAt"),
                endedAt = sessionJson.optLong("endedAt"),
                success = sessionJson.optBoolean("success"),
                averageLatencyMs = sessionJson.optInt("averageLatencyMs"),
                localInternetProvider = sessionJson.optBoolean("localInternetProvider"),
                gatewayNodeId = sessionJson.optString("gatewayNodeId"),
                gatewayNodeName = sessionJson.optString("gatewayNodeName"),
                gatewayNodeAddress = sessionJson.optString("gatewayNodeAddress"),
                relayPath = relayPath
            ),
            settlement = ServiceSettlement(
                sessionId = settlementJson.optString("sessionId"),
                validMegaBytes = settlementJson.optDouble("validMegaBytes"),
                burnAmount = settlementJson.optDouble("burnAmount"),
                gatewayReward = settlementJson.optDouble("gatewayReward"),
                relayRewards = relayRewards,
                builderReward = settlementJson.optDouble("builderReward"),
                treasuryReserve = settlementJson.optDouble("treasuryReserve"),
                validationScore = settlementJson.optDouble("validationScore"),
                notes = settlementJson.optString("notes")
            )
        )
    }
}
