package com.ghalbitnet.meshx2.economy

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InternetBridgeRequestLogManager {

    data class RequestLogEntry(
        val globalId: String,
        val alias: String,
        val timestamp: Long,
        val allowed: Boolean,
        val tier: String,
        val routeMode: String,
        val dailyUsedMb: Double,
        val dailyQuotaMb: Int,
        val detail: String,
        val source: String
    )

    data class Summary(
        val total: Int,
        val allowed: Int,
        val denied: Int
    )

    data class PeerDecisionSummary(
        val allowed: Int,
        val denied: Int
    )

    private const val PREFS_NAME = "internet_bridge_request_log"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 120

    fun record(
        context: Context,
        globalId: String,
        alias: String,
        decision: InternetBridgePolicyManager.Decision,
        source: String
    ) {
        val prefs =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val current =
            JSONArray(prefs.getString(KEY_ENTRIES, "[]"))

        current.put(
            JSONObject()
                .put("globalId", globalId)
                .put("alias", alias)
                .put("timestamp", System.currentTimeMillis())
                .put("allowed", decision.allowed)
                .put("tier", decision.userTier.name)
                .put("routeMode", decision.routeMode.name)
                .put("dailyUsedMb", decision.dailyUsedMb)
                .put("dailyQuotaMb", decision.dailyQuotaMb)
                .put("detail", decision.detail)
                .put("source", source)
        )

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

    fun recentForPeer(
        context: Context,
        globalId: String,
        limit: Int = 5
    ): List<RequestLogEntry> {
        val array =
            JSONArray(
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_ENTRIES, "[]")
            )

        val cleanId = globalId.trim().uppercase()
        val items = mutableListOf<RequestLogEntry>()

        for (index in array.length() - 1 downTo 0) {
            val item = deserialize(array.getJSONObject(index))
            if (item.globalId == cleanId) {
                items += item
            }
            if (items.size >= limit) {
                break
            }
        }

        return items
    }

    fun summary(
        context: Context
    ): Summary {
        val array =
            JSONArray(
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_ENTRIES, "[]")
            )

        var allowed = 0
        var denied = 0

        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val source =
                item.optString("source")
            if (source.startsWith("SESSION_")) {
                continue
            }
            if (item.optBoolean("allowed")) {
                allowed += 1
            } else {
                denied += 1
            }
        }

        return Summary(
            total = allowed + denied,
            allowed = allowed,
            denied = denied
        )
    }

    fun formatEntries(
        entries: List<RequestLogEntry>
    ): String {
        if (entries.isEmpty()) {
            return "Belum ada log akses bridge."
        }

        val timeFormat =
            SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

        return entries.joinToString("\n\n") { entry ->
            val label = if (entry.allowed) "DIIZINKAN" else "DITOLAK"
            buildString {
                append(timeFormat.format(Date(entry.timestamp)))
                append(" | ")
                append(label)
                append('\n')
                append("Tier ")
                append(entry.tier)
                append(" | Route ")
                append(entry.routeMode)
                append('\n')
                append("Hari ini ")
                append(String.format(Locale.US, "%.2f", entry.dailyUsedMb))
                append(" / ")
                append(entry.dailyQuotaMb)
                append(" MB")
                append('\n')
                append(entry.source)
                append(": ")
                append(entry.detail)
            }
        }
    }

    fun decisionSummaryForPeer(
        context: Context,
        globalId: String
    ): PeerDecisionSummary {
        val array =
            JSONArray(
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_ENTRIES, "[]")
            )

        val cleanId = globalId.trim().uppercase()
        var allowed = 0
        var denied = 0

        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            if (item.optString("globalId") != cleanId) {
                continue
            }
            val source =
                item.optString("source")
            if (source.startsWith("SESSION_")) {
                continue
            }
            if (item.optBoolean("allowed")) {
                allowed += 1
            } else {
                denied += 1
            }
        }

        return PeerDecisionSummary(
            allowed = allowed,
            denied = denied
        )
    }

    fun recordSessionStop(
        context: Context,
        globalId: String,
        alias: String,
        routeMode: String,
        dailyUsedMb: Double,
        dailyQuotaMb: Int,
        detail: String,
        source: String
    ) {
        val prefs =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val current =
            JSONArray(prefs.getString(KEY_ENTRIES, "[]"))

        current.put(
            JSONObject()
                .put("globalId", globalId)
                .put("alias", alias)
                .put("timestamp", System.currentTimeMillis())
                .put("allowed", true)
                .put("tier", "SESSION")
                .put("routeMode", routeMode)
                .put("dailyUsedMb", dailyUsedMb)
                .put("dailyQuotaMb", dailyQuotaMb)
                .put("detail", detail)
                .put("source", source)
        )

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

    fun recordSessionFailover(
        context: Context,
        globalId: String,
        alias: String,
        oldGatewayName: String,
        newGatewayName: String,
        newRouteMode: String,
        detail: String
    ) {
        val prefs =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val current =
            JSONArray(prefs.getString(KEY_ENTRIES, "[]"))

        current.put(
            JSONObject()
                .put("globalId", globalId)
                .put("alias", alias)
                .put("timestamp", System.currentTimeMillis())
                .put("allowed", true)
                .put("tier", "SESSION")
                .put("routeMode", newRouteMode)
                .put("dailyUsedMb", 0.0)
                .put("dailyQuotaMb", 0)
                .put("detail", "$oldGatewayName -> $newGatewayName. $detail")
                .put("source", "SESSION_FAILOVER")
        )

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

    private fun deserialize(
        source: JSONObject
    ): RequestLogEntry {
        return RequestLogEntry(
            globalId = source.optString("globalId"),
            alias = source.optString("alias"),
            timestamp = source.optLong("timestamp"),
            allowed = source.optBoolean("allowed"),
            tier = source.optString("tier"),
            routeMode = source.optString("routeMode"),
            dailyUsedMb = source.optDouble("dailyUsedMb"),
            dailyQuotaMb = source.optInt("dailyQuotaMb"),
            detail = source.optString("detail"),
            source = source.optString("source")
        )
    }
}
