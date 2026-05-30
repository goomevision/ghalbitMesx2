package com.ghalbitnet.meshx2.routing

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object IntelligentRouteMemory {
    private const val TAG = "GHALBIT-ROUTE"
    private const val TAG_MULTI = "GHALBIT-MULTIPATH"
    private const val PREFS = "ghalbit_route_memory"
    private const val KEY_HINTS = "route_hints"
    private const val MAX_HINTS_PER_DESTINATION = 4
    private const val MAX_TOTAL_HINTS = 300

    fun rememberHint(context: Context, hint: RouteHint) {
        val hintsByKey = getHints(context)
            .associateBy { routeKey(it.destinationId, it.nextHopId) }
            .toMutableMap()
        val existing = hintsByKey[routeKey(hint.destinationId, hint.nextHopId)]
        val merged =
            if (existing != null) {
                hint.copy(
                    trustScore = ((existing.trustScore + hint.trustScore + 5) / 2).coerceIn(0, 100),
                    latencyMs = mergeLatency(existing.latencyMs, hint.latencyMs),
                    hopCount = minOf(existing.hopCount, hint.hopCount),
                    lastSeen = maxOf(existing.lastSeen, hint.lastSeen)
                )
            } else {
                hint
            }
        hintsByKey[routeKey(hint.destinationId, hint.nextHopId)] = merged
        val trimmed = trimHints(hintsByKey.values.toList())
        saveHints(context, trimmed)
        val rank = getCandidateHints(context, hint.destinationId).mapIndexed { index, item ->
            "#${index + 1}:${item.nextHopId}/score=${scoreHint(item).score}/trust=${item.trustScore}"
        }
        Log.d(TAG, "Remembered route hint for ${hint.destinationId} via ${hint.nextHopId}")
        Log.d(TAG_MULTI, "remember destination=${hint.destinationId} candidates=${rank.joinToString(" ")}")
    }

    fun getHint(context: Context, destinationId: String, maxAgeMs: Long = 180000L): RouteHint? {
        return getCandidateHints(context, destinationId, maxAgeMs).firstOrNull()
    }

    fun getCandidateHints(context: Context, destinationId: String, maxAgeMs: Long = 180000L): List<RouteHint> {
        val now = System.currentTimeMillis()
        val candidates = getHints(context)
            .filter { it.destinationId == destinationId && now - it.lastSeen <= maxAgeMs }
            .sortedWith(compareByDescending<RouteHint> { scoreHint(it).score }.thenBy { it.hopCount }.thenBy { it.latencyMs })
        if (candidates.isNotEmpty()) {
            Log.d(TAG_MULTI, "select destination=$destinationId candidates=${candidates.joinToString { "${it.nextHopId}:${scoreHint(it).score}" }}")
        }
        return candidates
    }

    fun markRouteResult(context: Context, destinationId: String, nextHopId: String, success: Boolean, latencyMs: Long = 0L) {
        val hints = getHints(context).toMutableList()
        val index = hints.indexOfFirst { it.destinationId == destinationId && it.nextHopId == nextHopId }
        if (index < 0) return
        val old = hints[index]
        val updated =
            old.copy(
                trustScore = if (success) (old.trustScore + 8).coerceAtMost(100) else (old.trustScore - 18).coerceAtLeast(0),
                latencyMs = if (latencyMs > 0) mergeLatency(old.latencyMs, latencyMs) else old.latencyMs,
                lastSeen = if (success) System.currentTimeMillis() else old.lastSeen
            )
        hints[index] = updated
        saveHints(context, trimHints(hints))
        Log.d(TAG_MULTI, "result destination=$destinationId nextHop=$nextHopId success=$success trust=${updated.trustScore} latency=${updated.latencyMs}")
    }

    fun getAllHints(context: Context): List<RouteHint> {
        return getHints(context)
    }

    fun scoreHint(hint: RouteHint): BestPathScore {
        val ageSeconds = ((System.currentTimeMillis() - hint.lastSeen) / 1000L).toInt().coerceAtLeast(0)
        val freshnessBonus = (120 - ageSeconds).coerceAtLeast(0)
        val latencyPenalty = if (hint.latencyMs <= 0L) 0 else (hint.latencyMs / 15L).toInt()
        val score = (hint.trustScore * 2) + freshnessBonus - (hint.hopCount * 12) - latencyPenalty
        return BestPathScore(destinationId = hint.destinationId, score = score)
    }

    private fun getHints(context: Context): List<RouteHint> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_HINTS, "[]").orEmpty()
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    RouteHint(
                        destinationId = item.getString("destinationId"),
                        nextHopId = item.getString("nextHopId"),
                        latencyMs = item.optLong("latencyMs", 0L),
                        hopCount = item.optInt("hopCount", 1),
                        trustScore = item.optInt("trustScore", 50),
                        lastSeen = item.optLong("lastSeen", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    private fun saveHints(context: Context, hints: List<RouteHint>) {
        val array = JSONArray()
        hints.forEach { hint ->
            array.put(
                JSONObject()
                    .put("destinationId", hint.destinationId)
                    .put("nextHopId", hint.nextHopId)
                    .put("latencyMs", hint.latencyMs)
                    .put("hopCount", hint.hopCount)
                    .put("trustScore", hint.trustScore)
                    .put("lastSeen", hint.lastSeen)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_HINTS, array.toString()).apply()
    }

    private fun trimHints(hints: List<RouteHint>): List<RouteHint> {
        return hints
            .groupBy { it.destinationId }
            .flatMap { (_, group) ->
                group.sortedByDescending { scoreHint(it).score }.take(MAX_HINTS_PER_DESTINATION)
            }
            .sortedByDescending { scoreHint(it).score }
            .take(MAX_TOTAL_HINTS)
    }

    private fun routeKey(destinationId: String, nextHopId: String): String = "$destinationId@$nextHopId"

    private fun mergeLatency(old: Long, new: Long): Long {
        return when {
            old <= 0L -> new.coerceAtLeast(0L)
            new <= 0L -> old
            else -> ((old * 2) + new) / 3
        }
    }
}
