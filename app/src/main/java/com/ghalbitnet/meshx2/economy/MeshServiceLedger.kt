package com.ghalbitnet.meshx2.economy

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

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

        val totalValidatorReward =
            entries.sumOf { it.settlement.validatorReward }

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
            totalValidatorReward = totalValidatorReward,
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

    fun dailyBridgeUsageMb(
        context: Context,
        userGlobalId: String,
        now: Long = System.currentTimeMillis()
    ): Double {
        val startOfDay =
            Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

        return recentEntries(context, MAX_ENTRIES)
            .asSequence()
            .filter { it.session.usageMode == ServiceUsageMode.INTERNET_BRIDGE }
            .filter { it.session.userGlobalId == userGlobalId }
            .filter { it.session.endedAt >= startOfDay }
            .sumOf { it.session.totalMegaBytes }
    }

    fun peerSnapshot(
        context: Context,
        userGlobalId: String
    ): PeerServiceSnapshot {
        val entries =
            recentEntries(context, MAX_ENTRIES)
                .filter { it.session.userGlobalId == userGlobalId }

        return PeerServiceSnapshot(
            globalId = userGlobalId,
            sessionCount = entries.size,
            totalBytes = entries.sumOf { it.session.totalBytes },
            totalBurned = entries.sumOf { it.settlement.burnAmount },
            totalGatewayReward = entries.sumOf { it.settlement.gatewayReward },
            totalRelayReward = entries.sumOf { it.settlement.totalRelayReward },
            totalBuilderReward = entries.sumOf { it.settlement.builderReward },
            totalValidatorReward = entries.sumOf { it.settlement.validatorReward },
            totalTreasury = entries.sumOf { it.settlement.treasuryReserve },
            lastUpdatedAt = entries.maxOfOrNull { it.session.endedAt } ?: 0L
        )
    }

    fun recentGatewayUsageMb(
        context: Context,
        gatewayNodeId: String,
        limit: Int = 20
    ): Double {
        if (gatewayNodeId.isBlank()) return 0.0
        return recentEntries(context, limit)
            .filter { it.session.gatewayNodeId == gatewayNodeId }
            .sumOf { it.session.totalMegaBytes }
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

        val routeSegmentArray =
            JSONArray().apply {
                entry.session.routeSegments.forEach { segment ->
                    put(
                        JSONObject()
                            .put("gatewayNodeId", segment.gatewayNodeId)
                            .put("gatewayNodeName", segment.gatewayNodeName)
                            .put("gatewayNodeAddress", segment.gatewayNodeAddress)
                            .put("localGateway", segment.localGateway)
                            .put("routeMode", segment.routeMode)
                            .put("routeScore", segment.routeScore)
                            .put("startedAt", segment.startedAt)
                            .put("endedAt", segment.endedAt)
                            .put(
                                "relayPath",
                                JSONArray().apply {
                                    segment.relayPath.forEach { relay ->
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
                            )
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

        val gatewayRewardArray =
            JSONArray().apply {
                entry.settlement.gatewayRewards.forEach { reward ->
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
                    .put("usageMode", session.usageMode.name)
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
                    .put("stopReason", session.stopReason)
                    .put("relayPath", relayArray)
                    .put("routeSegments", routeSegmentArray)
            )
            .put(
                "settlement",
                JSONObject()
                    .put("sessionId", settlement.sessionId)
                    .put("validMegaBytes", settlement.validMegaBytes)
                    .put("familyMultiplier", settlement.familyMultiplier)
                    .put("pricingLabel", settlement.pricingLabel)
                    .put("userCharged", settlement.userCharged)
                    .put("burnAmount", settlement.burnAmount)
                    .put("gatewayReward", settlement.gatewayReward)
                    .put("gatewayRewards", gatewayRewardArray)
                    .put("builderReward", settlement.builderReward)
                    .put("validatorReward", settlement.validatorReward)
                    .put("treasuryReserve", settlement.treasuryReserve)
                    .put("validationScore", settlement.validationScore)
                    .put(
                        "proofScore",
                        JSONObject()
                            .put("gatewayProof", settlement.proofScore.gatewayProof)
                            .put("relayProof", settlement.proofScore.relayProof)
                            .put("validatorProof", settlement.proofScore.validatorProof)
                            .put("meshLocalProof", settlement.proofScore.meshLocalProof)
                            .put("overallProof", settlement.proofScore.overallProof)
                    )
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

        val routeSegmentsJson =
            sessionJson.optJSONArray("routeSegments") ?: JSONArray()
        val routeSegments =
            buildList {
                for (index in 0 until routeSegmentsJson.length()) {
                    val segment = routeSegmentsJson.getJSONObject(index)
                    val segmentRelayJson = segment.optJSONArray("relayPath") ?: JSONArray()
                    val segmentRelayPath =
                        buildList {
                            for (relayIndex in 0 until segmentRelayJson.length()) {
                                val relay = segmentRelayJson.getJSONObject(relayIndex)
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
                    add(
                        ServiceRouteSegment(
                            gatewayNodeId = segment.optString("gatewayNodeId"),
                            gatewayNodeName = segment.optString("gatewayNodeName"),
                            gatewayNodeAddress = segment.optString("gatewayNodeAddress"),
                            localGateway = segment.optBoolean("localGateway"),
                            routeMode = segment.optString("routeMode"),
                            routeScore = segment.optInt("routeScore", 0),
                            relayPath = segmentRelayPath,
                            startedAt = segment.optLong("startedAt"),
                            endedAt = segment.optLong("endedAt")
                        )
                    )
                }
            }

        val settlementJson =
            source.getJSONObject("settlement")
        val proofJson =
            settlementJson.optJSONObject("proofScore") ?: JSONObject()

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
        val gatewayRewardsJson =
            settlementJson.optJSONArray("gatewayRewards") ?: JSONArray()

        val gatewayRewards =
            buildList {
                for (index in 0 until gatewayRewardsJson.length()) {
                    val reward = gatewayRewardsJson.getJSONObject(index)
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
                usageMode = ServiceUsageMode.valueOf(sessionJson.optString("usageMode", ServiceUsageMode.APP_BONUS.name)),
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
                stopReason = sessionJson.optString("stopReason", "UNKNOWN"),
                relayPath = relayPath,
                routeSegments = routeSegments
            ),
            settlement = ServiceSettlement(
                sessionId = settlementJson.optString("sessionId"),
                validMegaBytes = settlementJson.optDouble("validMegaBytes"),
                familyMultiplier = settlementJson.optDouble("familyMultiplier", 1.0),
                pricingLabel = settlementJson.optString("pricingLabel", "BONUS APP"),
                userCharged = settlementJson.optBoolean("userCharged", false),
                burnAmount = settlementJson.optDouble("burnAmount"),
                gatewayReward = settlementJson.optDouble("gatewayReward"),
                gatewayRewards = gatewayRewards,
                relayRewards = relayRewards,
                builderReward = settlementJson.optDouble("builderReward"),
                validatorReward = settlementJson.optDouble("validatorReward"),
                treasuryReserve = settlementJson.optDouble("treasuryReserve"),
                validationScore = settlementJson.optDouble("validationScore"),
                proofScore = ServiceProofScore(
                    gatewayProof = proofJson.optDouble("gatewayProof", 0.0),
                    relayProof = proofJson.optDouble("relayProof", 0.0),
                    validatorProof = proofJson.optDouble("validatorProof", 0.0),
                    meshLocalProof = proofJson.optDouble("meshLocalProof", 0.0),
                    overallProof = proofJson.optDouble("overallProof", settlementJson.optDouble("validationScore", 0.0))
                ),
                notes = settlementJson.optString("notes")
            )
        )
    }
}
