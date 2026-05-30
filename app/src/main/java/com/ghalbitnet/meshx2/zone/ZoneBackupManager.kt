package com.ghalbitnet.meshx2.zone

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object ZoneBackupManager {
    private const val TAG = "GHALBIT-LEDGER"

    suspend fun exportZoneLedgerJson(
        context: Context,
        zoneId: String,
        sourceNodeId: String,
        role: BackupNodeRole = BackupNodeRole.ZONE_BACKUP
    ): String = withContext(Dispatchers.IO) {
        val ledger = ZoneLedgerStore.getZoneLedger(context, zoneId)
        val payload = JSONObject()
            .put("zoneId", zoneId)
            .put("sourceNodeId", sourceNodeId)
            .put("role", role.name)
            .put("exportedAt", System.currentTimeMillis())
            .put(
                "entries",
                JSONArray().apply {
                    ledger.entries.forEach { entry ->
                        put(
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
                }
            )
        Log.d(TAG, "Exported zone ledger backup for $zoneId with ${ledger.entries.size} entries")
        payload.toString()
    }

    suspend fun importZoneLedgerJson(context: Context, json: String): BackupReplica = withContext(Dispatchers.IO) {
        val payload = JSONObject(json)
        val zoneId = payload.getString("zoneId")
        val sourceNodeId = payload.optString("sourceNodeId", "unknown")
        val role = runCatching { BackupNodeRole.valueOf(payload.optString("role", BackupNodeRole.ZONE_BACKUP.name)) }
            .getOrDefault(BackupNodeRole.ZONE_BACKUP)
        val entries = payload.getJSONArray("entries")
        for (index in 0 until entries.length()) {
            val item = entries.getJSONObject(index)
            ZoneLedgerStore.upsertEntry(
                context,
                ZoneLedgerEntry(
                    nodeId = item.getString("nodeId"),
                    zoneId = item.optString("zoneId", zoneId),
                    publicKeyHash = item.optString("publicKeyHash", ""),
                    lastSeen = item.optLong("lastSeen", System.currentTimeMillis()),
                    routeHint = item.optString("routeHint", null),
                    trustScore = item.optInt("trustScore", 50),
                    expireAt = item.optLong("expireAt", 0L)
                )
            )
        }
        Log.d(TAG, "Imported zone ledger backup for $zoneId from $sourceNodeId")
        BackupReplica(
            nodeId = sourceNodeId,
            zoneId = zoneId,
            role = role,
            entryCount = entries.length()
        )
    }
}
