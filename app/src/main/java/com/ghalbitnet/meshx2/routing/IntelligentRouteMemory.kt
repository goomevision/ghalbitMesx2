package com.ghalbitnet.meshx2.routing

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object IntelligentRouteMemory {
    private const val TAG = "GHALBIT-ROUTE"
    private const val PREFS = "ghalbit_route_memory"
    private const val KEY_HINTS = "route_hints"

    fun rememberHint(context: Context, hint: RouteHint) {
        val hints = getHints(context).associateBy { it.destinationId }.toMutableMap()
        hints[hint.destinationId] = hint
        saveHints(context, hints.values.toList())
        Log.d(TAG, "Remembered route hint for ${hint.destinationId} via ${hint.nextHopId}")
    }

    fun getHint(context: Context, destinationId: String, maxAgeMs: Long = 180000L): RouteHint? {
        val now = System.currentTimeMillis()
        return getHints(context).firstOrNull {
            it.destinationId == destinationId && now - it.lastSeen <= maxAgeMs
        }
    }

    fun getAllHints(context: Context): List<RouteHint> {
        return getHints(context)
    }

    fun scoreHint(hint: RouteHint): BestPathScore {
        val freshnessBonus = (100 - ((System.currentTimeMillis() - hint.lastSeen) / 1000L).toInt()).coerceAtLeast(0)
        val score = (hint.trustScore * 2) + freshnessBonus - (hint.hopCount * 10) - (hint.latencyMs / 10L).toInt()
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
}
