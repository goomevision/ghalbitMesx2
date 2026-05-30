package com.ghalbitnet.meshx2.sos

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object SosAlertStore {
    private const val PREFS = "ghalbit_sos_alerts"
    private const val KEY_ALERTS = "alerts"

    fun getAll(context: Context): List<SosAlert> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ALERTS, "[]")
            .orEmpty()
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    SosAlert(
                        alertId = item.optString("alertId"),
                        sourceNodeId = item.optString("sourceNodeId"),
                        sourceGlobalId = item.optString("sourceGlobalId").ifBlank { null },
                        receivedAt = item.optLong("receivedAt"),
                        message = item.optString("message"),
                        routeHint = item.optString("routeHint").ifBlank { null },
                        isRead = item.optBoolean("isRead", false),
                        relayPath = item.optString("relayPath").ifBlank { null }
                    )
                )
            }
        }.sortedByDescending { it.receivedAt }
    }

    fun upsert(context: Context, alert: SosAlert) {
        val merged = getAll(context).associateBy { it.alertId }.toMutableMap()
        merged[alert.alertId] = alert
        save(context, merged.values.sortedByDescending { it.receivedAt })
        Log.d("GHALBIT-SOS-STORE", "saved alertId=${alert.alertId} node=${alert.sourceNodeId}")
    }

    fun markRead(context: Context, alertId: String) {
        val updated =
            getAll(context).map {
                if (it.alertId == alertId) it.copy(isRead = true) else it
            }
        save(context, updated)
    }

    fun clearReadItems(context: Context): Int {
        val current = getAll(context)
        val retained = current.filterNot { it.isRead }
        save(context, retained)
        return current.size - retained.size
    }

    private fun save(context: Context, alerts: List<SosAlert>) {
        val array = JSONArray()
        alerts.forEach { alert ->
            array.put(
                JSONObject()
                    .put("alertId", alert.alertId)
                    .put("sourceNodeId", alert.sourceNodeId)
                    .put("sourceGlobalId", alert.sourceGlobalId)
                    .put("receivedAt", alert.receivedAt)
                    .put("message", alert.message)
                    .put("routeHint", alert.routeHint)
                    .put("isRead", alert.isRead)
                    .put("relayPath", alert.relayPath)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ALERTS, array.toString())
            .apply()
    }
}
