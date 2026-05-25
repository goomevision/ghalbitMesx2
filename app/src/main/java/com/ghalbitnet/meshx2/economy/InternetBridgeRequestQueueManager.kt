package com.ghalbitnet.meshx2.economy

import android.content.Context
import com.ghalbitnet.meshx2.chat.GlobalContactDirectory
import org.json.JSONArray
import org.json.JSONObject

object InternetBridgeRequestQueueManager {

    enum class QueueStatus {
        WAITING,
        ACTIVE,
        DENIED
    }

    data class QueueEntry(
        val globalId: String,
        val alias: String,
        val requestedAt: Long,
        val tier: InternetBridgePolicyManager.UserTier,
        val reputationScore: Int,
        val reputationLabel: String,
        val routeMode: InternetBridgePolicyManager.RouteMode,
        val status: QueueStatus,
        val detail: String
    )

    data class Summary(
        val activeCount: Int,
        val waitingCount: Int,
        val deniedCount: Int,
        val activeAlias: String
    )

    private const val PREFS_NAME = "internet_bridge_request_queue"
    private const val KEY_QUEUE = "queue_entries"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun enqueue(
        context: Context,
        globalId: String,
        alias: String,
        decision: InternetBridgePolicyManager.Decision
    ): QueueEntry {
        val cleanId = GlobalContactDirectory.normalizeGlobalId(globalId)
        val entries =
            load(context)
                .filterNot { it.globalId == cleanId }
                .toMutableList()

        val hasActive =
            entries.any { it.status == QueueStatus.ACTIVE }

        val reputation =
            buildReputation(context, cleanId)

        val entry =
            QueueEntry(
                globalId = cleanId,
                alias = alias,
                requestedAt = System.currentTimeMillis(),
                tier = decision.userTier,
                reputationScore = reputation.score,
                reputationLabel = reputation.label,
                routeMode = decision.routeMode,
                status = when {
                    !decision.allowed -> QueueStatus.DENIED
                    hasActive -> QueueStatus.WAITING
                    else -> QueueStatus.ACTIVE
                },
                detail = decision.detail
            )

        entries += entry
        val normalized = normalize(entries)
        save(context, normalized)
        return normalized.first { it.globalId == cleanId }
    }

    fun release(
        context: Context,
        globalId: String
    ): Summary {
        val cleanId = GlobalContactDirectory.normalizeGlobalId(globalId)
        val entries =
            load(context)
                .filterNot { it.globalId == cleanId }
                .toMutableList()

        val normalized = normalize(entries)
        save(context, normalized)
        return buildSummary(normalized)
    }

    fun syncDecision(
        context: Context,
        globalId: String,
        alias: String,
        decision: InternetBridgePolicyManager.Decision
    ): QueueEntry? {
        val cleanId = GlobalContactDirectory.normalizeGlobalId(globalId)
        val entries = load(context).toMutableList()
        val index = entries.indexOfFirst { it.globalId == cleanId }
        if (index == -1) {
            return null
        }

        val current = entries[index]
        val reputation =
            buildReputation(context, cleanId)
        val updatedStatus =
            when {
                !decision.allowed -> QueueStatus.DENIED
                current.status == QueueStatus.ACTIVE -> QueueStatus.ACTIVE
                else -> QueueStatus.WAITING
            }

        entries[index] =
            current.copy(
                alias = alias,
                tier = decision.userTier,
                reputationScore = reputation.score,
                reputationLabel = reputation.label,
                routeMode = decision.routeMode,
                status = updatedStatus,
                detail = decision.detail
            )

        val normalized = normalize(entries)
        save(context, normalized)
        return normalized.firstOrNull { it.globalId == cleanId }
    }

    fun currentForPeer(
        context: Context,
        globalId: String
    ): QueueEntry? {
        val cleanId = GlobalContactDirectory.normalizeGlobalId(globalId)
        return load(context).firstOrNull { it.globalId == cleanId }
    }

    fun activePeer(
        context: Context
    ): QueueEntry? {
        return load(context).firstOrNull { it.status == QueueStatus.ACTIVE }
    }

    fun queuePositionForPeer(
        context: Context,
        globalId: String
    ): Int? {
        val cleanId = GlobalContactDirectory.normalizeGlobalId(globalId)
        val waiting =
            load(context).filter { it.status == QueueStatus.WAITING }
        val index =
            waiting.indexOfFirst { it.globalId == cleanId }
        return if (index >= 0) index + 1 else null
    }

    fun snapshot(
        context: Context
    ): List<QueueEntry> = load(context)

    fun reevaluate(
        context: Context
    ): Summary {
        val reevaluated =
            load(context).map { entry ->
                val decision =
                    InternetBridgePolicyManager.evaluate(context, entry.globalId)
                val alias =
                    GlobalContactDirectory.find(context, entry.globalId)?.alias ?: entry.alias
                val reputation =
                    buildReputation(context, entry.globalId)

                entry.copy(
                    alias = alias,
                    tier = decision.userTier,
                    reputationScore = reputation.score,
                    reputationLabel = reputation.label,
                    routeMode = decision.routeMode,
                    status = when {
                        !decision.allowed -> QueueStatus.DENIED
                        else -> QueueStatus.WAITING
                    },
                    detail = decision.detail
                )
            }

        val normalized =
            normalize(reevaluated)

        save(context, normalized)
        return buildSummary(normalized)
    }

    fun summary(
        context: Context
    ): Summary = buildSummary(load(context))

    private fun normalize(
        entries: List<QueueEntry>
    ): List<QueueEntry> {
        val sorted =
            entries
                .sortedWith(
                    compareBy<QueueEntry>(
                        { statusRank(it.status) },
                        { tierRank(it.tier) },
                        { -it.reputationScore },
                        { it.requestedAt }
                    )
                )
                .toMutableList()

        var activeExists = false

        for (index in sorted.indices) {
            val entry = sorted[index]
            when (entry.status) {
                QueueStatus.DENIED -> Unit
                QueueStatus.ACTIVE -> {
                    if (!activeExists) {
                        activeExists = true
                    } else {
                        sorted[index] = entry.copy(status = QueueStatus.WAITING)
                    }
                }
                QueueStatus.WAITING -> {
                    if (!activeExists) {
                        sorted[index] = entry.copy(status = QueueStatus.ACTIVE)
                        activeExists = true
                    }
                }
            }
        }

        return sorted
    }

    private fun buildSummary(
        entries: List<QueueEntry>
    ): Summary {
        val active =
            entries.firstOrNull { it.status == QueueStatus.ACTIVE }

        return Summary(
            activeCount = entries.count { it.status == QueueStatus.ACTIVE },
            waitingCount = entries.count { it.status == QueueStatus.WAITING },
            deniedCount = entries.count { it.status == QueueStatus.DENIED },
            activeAlias = active?.alias.orEmpty()
        )
    }

    private fun load(
        context: Context
    ): List<QueueEntry> {
        val source =
            prefs(context).getString(KEY_QUEUE, "[]").orEmpty()
        val array = JSONArray(source)
        val items = mutableListOf<QueueEntry>()
        for (index in 0 until array.length()) {
            items += deserialize(array.getJSONObject(index))
        }
        return items
    }

    private fun save(
        context: Context,
        entries: List<QueueEntry>
    ) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("globalId", entry.globalId)
                    .put("alias", entry.alias)
                    .put("requestedAt", entry.requestedAt)
                    .put("tier", entry.tier.name)
                    .put("reputationScore", entry.reputationScore)
                    .put("reputationLabel", entry.reputationLabel)
                    .put("routeMode", entry.routeMode.name)
                    .put("status", entry.status.name)
                    .put("detail", entry.detail)
            )
        }

        prefs(context)
            .edit()
            .putString(KEY_QUEUE, array.toString())
            .apply()
    }

    private fun deserialize(
        source: JSONObject
    ): QueueEntry {
        return QueueEntry(
            globalId = source.optString("globalId"),
            alias = source.optString("alias"),
            requestedAt = source.optLong("requestedAt"),
            tier = InternetBridgePolicyManager.UserTier.valueOf(
                source.optString("tier", InternetBridgePolicyManager.UserTier.STANDARD.name)
            ),
            reputationScore = source.optInt("reputationScore", 0),
            reputationLabel = source.optString("reputationLabel", "LEMAH"),
            routeMode = InternetBridgePolicyManager.RouteMode.valueOf(
                source.optString("routeMode", InternetBridgePolicyManager.RouteMode.UNAVAILABLE.name)
            ),
            status = QueueStatus.valueOf(
                source.optString("status", QueueStatus.WAITING.name)
            ),
            detail = source.optString("detail")
        )
    }

    private fun statusRank(
        status: QueueStatus
    ): Int {
        return when (status) {
            QueueStatus.ACTIVE -> 0
            QueueStatus.WAITING -> 1
            QueueStatus.DENIED -> 2
        }
    }

    private fun tierRank(
        tier: InternetBridgePolicyManager.UserTier
    ): Int {
        return when (tier) {
            InternetBridgePolicyManager.UserTier.PRIORITY -> 0
            InternetBridgePolicyManager.UserTier.STANDARD -> 1
            InternetBridgePolicyManager.UserTier.BLOCKED -> 2
        }
    }

    private fun buildReputation(
        context: Context,
        globalId: String
    ): PeerReputationManager.Reputation {
        val snapshot =
            MeshServiceLedger.peerSnapshot(context, globalId)
        val decisions =
            InternetBridgeRequestLogManager.decisionSummaryForPeer(context, globalId)
        return PeerReputationManager.calculate(snapshot, decisions)
    }
}
