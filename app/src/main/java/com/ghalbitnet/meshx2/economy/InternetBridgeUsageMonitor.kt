package com.ghalbitnet.meshx2.economy

import android.content.Context
import android.net.TrafficStats
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.core.network.ConnectivityStatusDetector
import com.ghalbitnet.meshx2.core.network.InternetGatewayRegistry
import com.ghalbitnet.meshx2.discovery.DiscoveryManager
import com.ghalbitnet.meshx2.model.MeshNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object InternetBridgeUsageMonitor {

    private const val PREFS_NAME = "internet_bridge_usage_monitor"
    private const val KEY_ACTIVE = "active_state"

    data class ActiveSession(
        val sessionId: String,
        val startedAt: Long,
        val baseRxBytes: Long,
        val baseTxBytes: Long,
        val routeMode: String,
        val gatewayNodeId: String,
        val gatewayNodeName: String,
        val gatewayNodeAddress: String,
        val localGateway: Boolean,
        val routeScore: Int,
        val routeKey: String,
        val selectedRelayPath: List<ServiceParticipant>,
        val routeSegments: List<ServiceRouteSegment>,
        val assignedPeerGlobalId: String,
        val assignedPeerAlias: String,
        val stopReason: String
    )

    data class MonitorSnapshot(
        val active: Boolean,
        val summary: String,
        val totalMb: Double = 0.0,
        val durationSec: Long = 0L,
        val routeMode: String = "",
        val assignedPeerGlobalId: String = "",
        val assignedPeerAlias: String = "",
        val estimatedPeerDailyMb: Double = 0.0,
        val peerQuotaMb: Int = 0
    )

    fun start(
        context: Context
    ): Boolean {
        if (activeSession(context) != null) {
            return false
        }

        val now = System.currentTimeMillis()
        val policyDecision =
            InternetBridgePolicyManager.evaluate(context)

        val localGateway =
            policyDecision.routeMode == InternetBridgePolicyManager.RouteMode.LOCAL_DIRECT

        val assignedPeer =
            InternetBridgeRequestQueueManager.activePeer(context)

        val active = ActiveSession(
            sessionId = "inet-${now}",
            startedAt = now,
            baseRxBytes = safeBytes(TrafficStats.getTotalRxBytes()),
            baseTxBytes = safeBytes(TrafficStats.getTotalTxBytes()),
            routeMode = policyDecision.routeMode.name,
            gatewayNodeId = policyDecision.gatewayId.ifBlank { if (localGateway) "local" else policyDecision.gatewayName },
            gatewayNodeName = if (localGateway) context.getString(R.string.gateway_this_device) else policyDecision.gatewayName,
            gatewayNodeAddress = if (localGateway) "local" else policyDecision.gatewayAddress,
            localGateway = localGateway,
            routeScore = policyDecision.routeScore,
            routeKey =
                buildString {
                    append(policyDecision.gatewayId)
                    append('|')
                    append(policyDecision.relayPath.joinToString(">") { it.nodeId.ifBlank { it.nodeName } })
                },
            selectedRelayPath = policyDecision.relayPath,
            routeSegments =
                listOf(
                    ServiceRouteSegment(
                        gatewayNodeId = policyDecision.gatewayId.ifBlank { if (localGateway) "local" else policyDecision.gatewayName },
                        gatewayNodeName = if (localGateway) context.getString(R.string.gateway_this_device) else policyDecision.gatewayName,
                        gatewayNodeAddress = if (localGateway) "local" else policyDecision.gatewayAddress,
                        localGateway = localGateway,
                        routeMode = policyDecision.routeMode.name,
                        routeScore = policyDecision.routeScore,
                        relayPath = policyDecision.relayPath,
                        startedAt = now,
                        endedAt = now
                    )
                ),
            assignedPeerGlobalId = assignedPeer?.globalId.orEmpty(),
            assignedPeerAlias = assignedPeer?.alias.orEmpty(),
            stopReason = context.getString(R.string.service_economy_stop_reason_manual)
        )

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE, serialize(active).toString())
            .apply()

        InternetGatewayLoadManager.reserve(context, active.gatewayNodeId)
        InternetRouteCooperationManager.reserve(context, active.routeKey)

        return true
    }

    suspend fun stopAndRecord(
        context: Context,
        ownerGlobalId: String
    ): ServiceLedgerEntry? = withContext(Dispatchers.IO) {
        val active = activeSession(context) ?: return@withContext null
        clear(context)
        InternetGatewayLoadManager.release(context, active.gatewayNodeId)
        InternetRouteCooperationManager.release(context, active.routeKey)

        val now = System.currentTimeMillis()
        val totalRx = safeBytes(TrafficStats.getTotalRxBytes())
        val totalTx = safeBytes(TrafficStats.getTotalTxBytes())
        val downBytes = maxOf(0L, totalRx - active.baseRxBytes)
        val upBytes = maxOf(0L, totalTx - active.baseTxBytes)
        val durationMs = maxOf(1L, now - active.startedAt)

        val nodes = DiscoveryManager.discoverNodes()
        val relayPath = observedRelayPath(context, nodes, active.selectedRelayPath)

        val session = ServiceSessionRecord(
            sessionId = active.sessionId,
            serviceFamily = ServiceFamily.INTERNET,
            usageMode = ServiceUsageMode.INTERNET_BRIDGE,
            userGlobalId = active.assignedPeerGlobalId.ifBlank { ownerGlobalId },
            bytesUp = upBytes,
            bytesDown = downBytes,
            durationMs = durationMs,
            startedAt = active.startedAt,
            endedAt = now,
            success = (upBytes + downBytes) > 0L,
            averageLatencyMs = if (relayPath.isEmpty()) 90 else (90 + relayPath.size * 18),
            localInternetProvider = active.localGateway,
            gatewayNodeId = active.gatewayNodeId,
            gatewayNodeName = active.gatewayNodeName,
            gatewayNodeAddress = active.gatewayNodeAddress,
            stopReason = active.stopReason,
            relayPath = relayPath,
            routeSegments = finalizedSegments(active, now)
        )

        val settlement = MeshServiceFormula.settle(context, session)
        MeshEconomySettlementEngine.applySettlement(context, ownerGlobalId, session, settlement)
        val entry = ServiceLedgerEntry(session, settlement)
        MeshServiceLedger.record(context, entry)

        if (active.assignedPeerGlobalId.isNotBlank()) {
            val peerDecision =
                InternetBridgePolicyManager.evaluate(context, active.assignedPeerGlobalId)
            InternetBridgeRequestLogManager.recordSessionStop(
                context = context,
                globalId = active.assignedPeerGlobalId,
                alias = active.assignedPeerAlias.ifBlank { active.assignedPeerGlobalId },
                routeMode = active.routeMode,
                dailyUsedMb = peerDecision.dailyUsedMb,
                dailyQuotaMb = peerDecision.dailyQuotaMb,
                detail = active.stopReason,
                source = "SESSION_STOP"
            )
            InternetBridgeRequestQueueManager.release(context, active.assignedPeerGlobalId)
        }

        entry
    }

    fun snapshot(
        context: Context
    ): MonitorSnapshot {
        val active = activeSession(context)
        if (active == null) {
            return MonitorSnapshot(
                active = false,
                summary = context.getString(R.string.internet_bridge_monitor_idle),
                routeMode = InternetBridgePolicyManager.RouteMode.UNAVAILABLE.name
            )
        }

        val now = System.currentTimeMillis()
        val totalRx = safeBytes(TrafficStats.getTotalRxBytes())
        val totalTx = safeBytes(TrafficStats.getTotalTxBytes())
        val downBytes = maxOf(0L, totalRx - active.baseRxBytes)
        val upBytes = maxOf(0L, totalTx - active.baseTxBytes)
        val totalMb = (upBytes + downBytes) / 1024.0 / 1024.0
        val seconds = maxOf(1L, (now - active.startedAt) / 1000L)

        val gatewayLabel =
            if (active.gatewayNodeName.isBlank()) {
                context.getString(R.string.gateway_active_none)
            } else {
                active.gatewayNodeName
            }

        val peerLabel =
            active.assignedPeerAlias.ifBlank {
                context.getString(R.string.internet_bridge_peer_local_owner)
            }

        val peerDecision =
            if (active.assignedPeerGlobalId.isNotBlank()) {
                InternetBridgePolicyManager.evaluate(context, active.assignedPeerGlobalId)
            } else {
                null
            }

        val estimatedPeerDailyMb =
            if (peerDecision != null) {
                peerDecision.dailyUsedMb + totalMb
            } else {
                totalMb
            }

        val peerQuotaMb =
            peerDecision?.dailyQuotaMb ?: 0

        return MonitorSnapshot(
            active = true,
            summary = buildString {
                append(
                    context.getString(
                        R.string.internet_bridge_monitor_active,
                        totalMb,
                        seconds,
                        gatewayLabel,
                        peerLabel,
                        estimatedPeerDailyMb,
                        peerQuotaMb
                    )
                )
                append("\nSkor rute: ")
                append(active.routeScore)
                if (active.selectedRelayPath.isNotEmpty()) {
                    append("\nRelay terpilih: ")
                    append(active.selectedRelayPath.joinToString(" -> ") { it.nodeName })
                }
            },
            totalMb = totalMb,
            durationSec = seconds,
            routeMode = active.routeMode,
            assignedPeerGlobalId = active.assignedPeerGlobalId,
            assignedPeerAlias = active.assignedPeerAlias,
            estimatedPeerDailyMb = estimatedPeerDailyMb,
            peerQuotaMb = peerQuotaMb
        )
    }

    fun activeSessionSnapshot(
        context: Context
    ): ActiveSession? {
        return activeSession(context)
    }

    private fun observedRelayPath(
        context: Context,
        nodes: List<MeshNode>,
        plannedPath: List<ServiceParticipant>
    ): List<ServiceParticipant> {
        val observed = ServicePathRecorder.recentRelayParticipants(context, 4)
        if (observed.isNotEmpty()) {
            return observed
                .plus(plannedPath)
                .distinctBy { "${it.nodeId}|${it.nodeAddress}" }
        }

        if (plannedPath.isNotEmpty()) {
            return plannedPath
        }

        return nodes
            .filter { it.online && it.relay }
            .sortedByDescending { it.trusted }
            .take(3)
            .mapIndexed { index, node ->
                ServiceParticipant(
                    nodeId = "${node.name}-inet-$index",
                    nodeName = node.name,
                    nodeAddress = node.ipAddress,
                    role = ServiceRole.RELAY,
                    local = false,
                    trustScore = node.trusted.coerceIn(10, 100)
                )
            }
    }

    private fun activeSession(
        context: Context
    ): ActiveSession? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE, null)
            ?: return null

        return deserialize(JSONObject(raw))
    }

    private fun clear(
        context: Context
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ACTIVE)
            .apply()
    }

    fun updateStopReason(
        context: Context,
        reason: String
    ) {
        val active =
            activeSession(context) ?: return
        val updated =
            active.copy(stopReason = reason)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE, serialize(updated).toString())
            .apply()
    }

    fun updateActiveRoute(
        context: Context,
        decision: InternetBridgePolicyManager.Decision
    ): Boolean {
        val active =
            activeSession(context) ?: return false

        val localGateway =
            decision.routeMode == InternetBridgePolicyManager.RouteMode.LOCAL_DIRECT
        val nextGatewayNodeId =
            decision.gatewayId.ifBlank {
                if (localGateway) "local" else decision.gatewayName
            }
        val nextGatewayNodeName =
            if (localGateway) context.getString(R.string.gateway_this_device) else decision.gatewayName
        val nextGatewayNodeAddress =
            if (localGateway) "local" else decision.gatewayAddress
        val nextRouteKey =
            buildString {
                append(decision.gatewayId)
                append('|')
                append(decision.relayPath.joinToString(">") { it.nodeId.ifBlank { it.nodeName } })
            }

        val changed =
            active.gatewayNodeId != nextGatewayNodeId ||
                active.routeKey != nextRouteKey ||
                active.selectedRelayPath != decision.relayPath ||
                active.routeScore != decision.routeScore

        if (!changed) {
            return false
        }

        InternetGatewayLoadManager.release(context, active.gatewayNodeId)
        InternetRouteCooperationManager.release(context, active.routeKey)

        val updated =
            active.copy(
                routeMode = decision.routeMode.name,
                gatewayNodeId = nextGatewayNodeId,
                gatewayNodeName = nextGatewayNodeName,
                gatewayNodeAddress = nextGatewayNodeAddress,
                localGateway = localGateway,
                routeScore = decision.routeScore,
                routeKey = nextRouteKey,
                selectedRelayPath = decision.relayPath,
                routeSegments =
                    active.routeSegments
                        .dropLast(1)
                        .plus(
                            active.routeSegments.lastOrNull()?.copy(
                                endedAt = System.currentTimeMillis()
                            )
                        )
                        .filterNotNull()
                        .plus(
                            ServiceRouteSegment(
                                gatewayNodeId = nextGatewayNodeId,
                                gatewayNodeName = nextGatewayNodeName,
                                gatewayNodeAddress = nextGatewayNodeAddress,
                                localGateway = localGateway,
                                routeMode = decision.routeMode.name,
                                routeScore = decision.routeScore,
                                relayPath = decision.relayPath,
                                startedAt = System.currentTimeMillis(),
                                endedAt = System.currentTimeMillis()
                            )
                        )
            )

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE, serialize(updated).toString())
            .apply()

        InternetGatewayLoadManager.reserve(context, updated.gatewayNodeId)
        InternetRouteCooperationManager.reserve(context, updated.routeKey)
        return true
    }

    private fun serialize(
        active: ActiveSession
    ): JSONObject {
        return JSONObject()
            .put("sessionId", active.sessionId)
            .put("startedAt", active.startedAt)
            .put("baseRxBytes", active.baseRxBytes)
            .put("baseTxBytes", active.baseTxBytes)
            .put("routeMode", active.routeMode)
            .put("gatewayNodeId", active.gatewayNodeId)
            .put("gatewayNodeName", active.gatewayNodeName)
            .put("gatewayNodeAddress", active.gatewayNodeAddress)
            .put("localGateway", active.localGateway)
            .put("routeScore", active.routeScore)
            .put("routeKey", active.routeKey)
            .put(
                "selectedRelayPath",
                JSONArray().apply {
                    active.selectedRelayPath.forEach { relay ->
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
            .put(
                "routeSegments",
                JSONArray().apply {
                    active.routeSegments.forEach { segment ->
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
            )
            .put("assignedPeerGlobalId", active.assignedPeerGlobalId)
            .put("assignedPeerAlias", active.assignedPeerAlias)
            .put("stopReason", active.stopReason)
    }

    private fun deserialize(
        source: JSONObject
    ): ActiveSession {
        val relayArray =
            source.optJSONArray("selectedRelayPath") ?: JSONArray()
        val relayPath =
            buildList {
                for (index in 0 until relayArray.length()) {
                    val relay = relayArray.getJSONObject(index)
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
        val segmentArray =
            source.optJSONArray("routeSegments") ?: JSONArray()
        val routeSegments =
            buildList {
                for (index in 0 until segmentArray.length()) {
                    val segment = segmentArray.getJSONObject(index)
                    val segmentRelayArray = segment.optJSONArray("relayPath") ?: JSONArray()
                    val segmentRelays =
                        buildList {
                            for (relayIndex in 0 until segmentRelayArray.length()) {
                                val relay = segmentRelayArray.getJSONObject(relayIndex)
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
                            relayPath = segmentRelays,
                            startedAt = segment.optLong("startedAt"),
                            endedAt = segment.optLong("endedAt")
                        )
                    )
                }
            }
        return ActiveSession(
            sessionId = source.optString("sessionId"),
            startedAt = source.optLong("startedAt"),
            baseRxBytes = source.optLong("baseRxBytes"),
            baseTxBytes = source.optLong("baseTxBytes"),
            routeMode = source.optString("routeMode", InternetBridgePolicyManager.RouteMode.UNAVAILABLE.name),
            gatewayNodeId = source.optString("gatewayNodeId"),
            gatewayNodeName = source.optString("gatewayNodeName"),
            gatewayNodeAddress = source.optString("gatewayNodeAddress"),
            localGateway = source.optBoolean("localGateway"),
            routeScore = source.optInt("routeScore", 0),
            routeKey = source.optString("routeKey"),
            selectedRelayPath = relayPath,
            routeSegments = routeSegments,
            assignedPeerGlobalId = source.optString("assignedPeerGlobalId"),
            assignedPeerAlias = source.optString("assignedPeerAlias"),
            stopReason = source.optString("stopReason", "MANUAL")
        )
    }

    private fun finalizedSegments(
        active: ActiveSession,
        endedAt: Long
    ): List<ServiceRouteSegment> {
        if (active.routeSegments.isEmpty()) {
            return emptyList()
        }
        return active.routeSegments.mapIndexed { index, segment ->
            if (index == active.routeSegments.lastIndex) {
                segment.copy(endedAt = endedAt)
            } else {
                segment
            }
        }
    }

    private fun safeBytes(
        value: Long
    ): Long {
        return if (value < 0L) 0L else value
    }
}
