package com.ghalbitnet.meshx2.zone

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object ZoneLedgerStore {
    private const val TAG = "GHALBIT-LEDGER"
    private const val PREFS = "ghalbit_zone_ledger"
    private const val KEY_LEDGER = "zone_ledger_entries"
    private val lock = Mutex()

    suspend fun upsertEntry(context: Context, entry: ZoneLedgerEntry) {
        lock.withLock {
            val entries = loadEntries(context).associateBy { it.nodeId to it.zoneId }.toMutableMap()
            entries[entry.nodeId to entry.zoneId] = entry
            saveEntries(context, entries.values.toList())
            Log.d(TAG, "Upsert zone ledger entry node=${entry.nodeId} zone=${entry.zoneId}")
        }
    }

    suspend fun getZoneLedger(context: Context, zoneId: String): LocalZoneLedger = lock.withLock {
        val entries = loadEntries(context).filter { it.zoneId == zoneId }.sortedByDescending { it.lastSeen }
        LocalZoneLedger(zoneId = zoneId, entries = entries, updatedAt = System.currentTimeMillis())
    }

    suspend fun getAllEntries(context: Context): List<ZoneLedgerEntry> = lock.withLock {
        loadEntries(context)
    }

    suspend fun removeExpired(context: Context, now: Long = System.currentTimeMillis()): Int {
        return lock.withLock {
            val entries = loadEntries(context)
            val active = entries.filter { it.expireAt <= 0L || it.expireAt > now }
            val removed = entries.size - active.size
            if (removed > 0) {
                saveEntries(context, active)
                Log.d(TAG, "Removed $removed expired zone ledger entries")
            }
            removed
        }
    }

    private suspend fun loadEntries(context: Context): List<ZoneLedgerEntry> = withContext(Dispatchers.IO) {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LEDGER, "[]").orEmpty()
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    ZoneLedgerEntry(
                        nodeId = item.getString("nodeId"),
                        zoneId = item.getString("zoneId"),
                        publicKeyHash = item.optString("publicKeyHash", ""),
                        lastSeen = item.optLong("lastSeen", 0L),
                        routeHint = item.optString("routeHint", null),
                        trustScore = item.optInt("trustScore", 50),
                        expireAt = item.optLong("expireAt", 0L)
                    )
                )
            }
        }
    }

    private fun saveEntries(context: Context, entries: List<ZoneLedgerEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("nodeId", entry.nodeId)
                    .put("zoneId", entry.zoneId)
                    .put("publicKeyHash", entry.publicKeyHash)
                    .put("lastSeen", entry.lastSeen)
                    .put("routeHint", entry.routeHint)
                    .put("trustScore", entry.trustScore)
                    .put("expireAt", entry.expireAt)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LEDGER, array.toString())
            .apply()
    }
}
