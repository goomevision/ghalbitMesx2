package com.ghalbitnet.meshx2.chat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object GroupBroadcastManager {

    data class RecipientStatus(
        val peerName: String,
        val displayName: String,
        val delivered: Boolean
    )

    data class BroadcastEntry(
        val group: String,
        val message: String,
        val successCount: Int,
        val totalCount: Int,
        val timestamp: Long,
        val recipients: List<RecipientStatus>
    )

    private const val PREFS_NAME = "group_broadcast_history"
    private const val KEY_HISTORY = "history"

    fun defaultTemplates(context: Context): List<String> {
        return listOf(
            context.getString(com.ghalbitnet.meshx2.R.string.broadcast_template_safe),
            context.getString(com.ghalbitnet.meshx2.R.string.broadcast_template_help),
            context.getString(com.ghalbitnet.meshx2.R.string.broadcast_template_gather),
            context.getString(com.ghalbitnet.meshx2.R.string.broadcast_template_update)
        )
    }

    fun saveHistory(
        context: Context,
        entry: BroadcastEntry
    ) {
        val history =
            JSONArray(
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_HISTORY, "[]")
            )

        history.put(
            JSONObject()
                .put("group", entry.group)
                .put("message", entry.message)
                .put("successCount", entry.successCount)
                .put("totalCount", entry.totalCount)
                .put("timestamp", entry.timestamp)
                .put(
                    "recipients",
                    JSONArray().apply {
                        entry.recipients.forEach { recipient ->
                            put(
                                JSONObject()
                                    .put("peerName", recipient.peerName)
                                    .put("displayName", recipient.displayName)
                                    .put("delivered", recipient.delivered)
                            )
                        }
                    }
                )
        )

        while (history.length() > 40) {
            history.remove(0)
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, history.toString())
            .apply()
    }

    fun getHistoryForGroup(
        context: Context,
        group: String
    ): List<BroadcastEntry> {
        val raw =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_HISTORY, "[]")
                ?: "[]"

        val array = JSONArray(raw)
        val items = mutableListOf<BroadcastEntry>()

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            if (!item.optString("group").equals(group, ignoreCase = true)) {
                continue
            }

            items += BroadcastEntry(
                group = item.optString("group"),
                message = item.optString("message"),
                successCount = item.optInt("successCount"),
                totalCount = item.optInt("totalCount"),
                timestamp = item.optLong("timestamp"),
                recipients = buildRecipients(item.optJSONArray("recipients"))
            )
        }

        return items.sortedByDescending { it.timestamp }
    }

    private fun buildRecipients(
        array: JSONArray?
    ): List<RecipientStatus> {
        if (array == null) {
            return emptyList()
        }

        val items = mutableListOf<RecipientStatus>()

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            items += RecipientStatus(
                peerName = item.optString("peerName"),
                displayName = item.optString("displayName"),
                delivered = item.optBoolean("delivered")
            )
        }

        return items
    }
}
