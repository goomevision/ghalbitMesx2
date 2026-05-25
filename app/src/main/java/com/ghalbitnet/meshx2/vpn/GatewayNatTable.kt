package com.ghalbitnet.meshx2.vpn

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object GatewayNatTable {

    private const val PREFS_NAME = "ghalbit_gateway_nat_table"
    private const val KEY_ENTRIES = "entries"
    private const val ENTRY_TTL_MS = 120_000L

    data class Entry(
        val clientNodeId: String,
        val sessionId: String,
        val sourceAddress: String,
        val sourcePort: Int,
        val destinationAddress: String,
        val destinationPort: Int,
        val protocol: String,
        val lastSeen: Long
    )

    @Synchronized
    fun upsert(
        context: Context,
        entry: Entry
    ) {
        val active = load(context).filterNot {
            it.clientNodeId == entry.clientNodeId &&
                it.sessionId == entry.sessionId &&
                it.sourceAddress == entry.sourceAddress &&
                it.sourcePort == entry.sourcePort &&
                it.destinationAddress == entry.destinationAddress &&
                it.destinationPort == entry.destinationPort &&
                it.protocol == entry.protocol
        }.toMutableList()
        active += entry
        save(context, active)
    }

    @Synchronized
    fun cleanup(context: Context) {
        val now = System.currentTimeMillis()
        save(
            context,
            load(context).filter { now - it.lastSeen <= ENTRY_TTL_MS }
        )
    }

    @Synchronized
    fun snapshot(context: Context): List<Entry> {
        cleanup(context)
        return load(context)
    }

    @Synchronized
    fun find(
        context: Context,
        clientNodeId: String,
        sessionId: String,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
        protocol: String
    ): Entry? {
        return load(context).firstOrNull {
            it.clientNodeId == clientNodeId &&
                it.sessionId == sessionId &&
                it.sourceAddress == sourceAddress &&
                it.sourcePort == sourcePort &&
                it.destinationAddress == destinationAddress &&
                it.destinationPort == destinationPort &&
                it.protocol == protocol
        }
    }

    @Synchronized
    fun remove(
        context: Context,
        clientNodeId: String,
        sessionId: String,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
        protocol: String
    ) {
        save(
            context,
            load(context).filterNot {
                it.clientNodeId == clientNodeId &&
                    it.sessionId == sessionId &&
                    it.sourceAddress == sourceAddress &&
                    it.sourcePort == sourcePort &&
                    it.destinationAddress == destinationAddress &&
                    it.destinationPort == destinationPort &&
                    it.protocol == protocol
            }
        )
    }

    private fun load(context: Context): List<Entry> {
        val raw =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ENTRIES, "[]")
                .orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    Entry(
                        clientNodeId = item.optString("clientNodeId"),
                        sessionId = item.optString("sessionId"),
                        sourceAddress = item.optString("sourceAddress"),
                        sourcePort = item.optInt("sourcePort"),
                        destinationAddress = item.optString("destinationAddress"),
                        destinationPort = item.optInt("destinationPort"),
                        protocol = item.optString("protocol"),
                        lastSeen = item.optLong("lastSeen")
                    )
                )
            }
        }
    }

    private fun save(
        context: Context,
        entries: List<Entry>
    ) {
        val array =
            JSONArray().apply {
                entries.forEach { entry ->
                    put(
                        JSONObject()
                            .put("clientNodeId", entry.clientNodeId)
                            .put("sessionId", entry.sessionId)
                            .put("sourceAddress", entry.sourceAddress)
                            .put("sourcePort", entry.sourcePort)
                            .put("destinationAddress", entry.destinationAddress)
                            .put("destinationPort", entry.destinationPort)
                            .put("protocol", entry.protocol)
                            .put("lastSeen", entry.lastSeen)
                    )
                }
            }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENTRIES, array.toString())
            .apply()
    }
}
