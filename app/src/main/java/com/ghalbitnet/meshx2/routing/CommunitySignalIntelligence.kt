package com.ghalbitnet.meshx2.routing

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object CommunitySignalIntelligence {
    private const val TAG = "GHALBIT-COMMUNITY-SIGNAL"
    private const val MAX_SHARED_HINTS = 12
    private const val MIN_SHARE_SCORE = 90

    data class SharedRouteInsight(
        val destinationId: String,
        val nextHopId: String,
        val score: Int,
        val trustScore: Int,
        val hopCount: Int,
        val latencyMs: Long,
        val ageMs: Long
    )

    fun buildSnapshot(context: Context): List<SharedRouteInsight> {
        val now = System.currentTimeMillis()
        val insights = IntelligentRouteMemory.getAllHints(context)
            .map { hint ->
                SharedRouteInsight(
                    destinationId = hint.destinationId,
                    nextHopId = hint.nextHopId,
                    score = IntelligentRouteMemory.scoreHint(hint).score,
                    trustScore = hint.trustScore,
                    hopCount = hint.hopCount,
                    latencyMs = hint.latencyMs,
                    ageMs = now - hint.lastSeen
                )
            }
            .filter { it.score >= MIN_SHARE_SCORE && it.ageMs <= 5 * 60_000L }
            .sortedByDescending { it.score }
            .take(MAX_SHARED_HINTS)
        Log.d(TAG, "snapshot count=${insights.size} best=${insights.firstOrNull()?.destinationId ?: "-"}")
        return insights
    }

    fun buildSnapshotPayload(context: Context): String {
        val array = JSONArray()
        buildSnapshot(context).forEach { insight ->
            array.put(
                JSONObject()
                    .put("destinationId", insight.destinationId)
                    .put("nextHopId", insight.nextHopId)
                    .put("score", insight.score)
                    .put("trustScore", insight.trustScore)
                    .put("hopCount", insight.hopCount)
                    .put("latencyMs", insight.latencyMs)
                    .put("ageMs", insight.ageMs)
            )
        }
        return JSONObject()
            .put("type", "COMMUNITY_SIGNAL_SNAPSHOT")
            .put("createdAt", System.currentTimeMillis())
            .put("routes", array)
            .toString()
    }

    fun mergeSnapshot(context: Context, payload: String, sourceNodeId: String) {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return
        val routes = json.optJSONArray("routes") ?: return
        var accepted = 0
        for (i in 0 until routes.length()) {
            val item = routes.optJSONObject(i) ?: continue
            val destinationId = item.optString("destinationId")
            val nextHopId = item.optString("nextHopId")
            val remoteScore = item.optInt("score", 0)
            val trustScore = item.optInt("trustScore", 0)
            val hopCount = item.optInt("hopCount", 1)
            val latencyMs = item.optLong("latencyMs", 0L)
            if (destinationId.isBlank() || nextHopId.isBlank()) continue
            if (remoteScore < MIN_SHARE_SCORE) continue
            if (destinationId == sourceNodeId) continue

            val communityTrust = ((trustScore * 0.65) + 20).toInt().coerceIn(25, 85)
            IntelligentRouteMemory.rememberHint(
                context,
                RouteHint(
                    destinationId = destinationId,
                    nextHopId = nextHopId,
                    latencyMs = latencyMs,
                    hopCount = (hopCount + 1).coerceAtLeast(1),
                    trustScore = communityTrust,
                    lastSeen = System.currentTimeMillis()
                )
            )
            accepted++
        }
        Log.d(TAG, "merge source=$sourceNodeId accepted=$accepted total=${routes.length()}")
    }
}
