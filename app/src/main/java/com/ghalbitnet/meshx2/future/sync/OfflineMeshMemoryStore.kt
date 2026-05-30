package com.ghalbitnet.meshx2.future.sync

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.model.MeshNode
import com.ghalbitnet.meshx2.routing.MeshRoute
import com.ghalbitnet.meshx2.zone.ZoneLedgerEntry
import org.json.JSONArray
import org.json.JSONObject

object OfflineMeshMemoryStore {
    private const val TAG = "GHALBIT-LEDGER"
    private const val PREFS = "ghalbit_offline_mesh_memory"
    private const val KEY_SNAPSHOT = "offline_mesh_snapshot"

    fun saveSnapshot(context: Context, snapshot: OfflineMeshMemorySnapshot) {
        val payload = JSONObject()
            .put("savedAt", snapshot.savedAt)
            .put("knownZones", JSONArray(snapshot.knownZones))
            .put("trustedRelays", JSONArray(snapshot.trustedRelays))
            .put("pendingPackets", JSONArray(snapshot.pendingPackets))
            .put(
                "knownNodes",
                JSONArray().apply {
                    snapshot.knownNodes.forEach { node ->
                        put(
                            JSONObject()
                                .put("name", node.name)
                                .put("ipAddress", node.ipAddress)
                                .put("online", node.online)
                                .put("lastSeen", node.lastSeen)
                                .put("latency", node.latency)
                                .put("trusted", node.trusted)
                        )
                    }
                }
            )
            .put(
                "lastRoutes",
                JSONArray().apply {
                    snapshot.lastRoutes.forEach { route ->
                        put(
                            JSONObject()
                                .put("destination", route.destination)
                                .put("nextHop", route.nextHop)
                                .put("hopCount", route.hopCount)
                                .put("updatedAt", route.updatedAt)
                        )
                    }
                }
            )
            .put(
                "zoneEntries",
                JSONArray().apply {
                    snapshot.zoneEntries.forEach { entry ->
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
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SNAPSHOT, payload.toString()).apply()
        Log.d(TAG, "Saved offline mesh memory snapshot")
    }

    fun loadSnapshot(context: Context): OfflineMeshMemorySnapshot? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SNAPSHOT, null) ?: return null
        val payload = JSONObject(raw)
        return OfflineMeshMemorySnapshot(
            knownNodes = parseNodes(payload.optJSONArray("knownNodes") ?: JSONArray()),
            knownZones = parseStrings(payload.optJSONArray("knownZones") ?: JSONArray()),
            lastRoutes = parseRoutes(payload.optJSONArray("lastRoutes") ?: JSONArray()),
            trustedRelays = parseStrings(payload.optJSONArray("trustedRelays") ?: JSONArray()),
            pendingPackets = parseStrings(payload.optJSONArray("pendingPackets") ?: JSONArray()),
            zoneEntries = parseZoneEntries(payload.optJSONArray("zoneEntries") ?: JSONArray()),
            savedAt = payload.optLong("savedAt", System.currentTimeMillis())
        )
    }

    fun enqueueDelayedSyncSummary(snapshot: OfflineMeshMemorySnapshot) {
        OfflineSyncManager.add(
            SyncItem(
                id = "offline-memory-${snapshot.savedAt}",
                type = "OFFLINE_MESH_MEMORY",
                payload = "nodes=${snapshot.knownNodes.size};zones=${snapshot.knownZones.size};routes=${snapshot.lastRoutes.size}"
            )
        )
    }

    private fun parseStrings(array: JSONArray): List<String> = buildList {
        for (index in 0 until array.length()) add(array.getString(index))
    }

    private fun parseNodes(array: JSONArray): List<MeshNode> = buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            add(
                MeshNode(
                    name = item.optString("name", ""),
                    ipAddress = item.optString("ipAddress", ""),
                    online = item.optBoolean("online", false),
                    lastSeen = item.optLong("lastSeen", 0L),
                    latency = item.optInt("latency", 0),
                    trusted = item.optInt("trusted", 50)
                )
            )
        }
    }

    private fun parseRoutes(array: JSONArray): List<MeshRoute> = buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            add(
                MeshRoute(
                    destination = item.optString("destination", ""),
                    nextHop = item.optString("nextHop", ""),
                    hopCount = item.optInt("hopCount", 1),
                    updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }
    }

    private fun parseZoneEntries(array: JSONArray): List<ZoneLedgerEntry> = buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            add(
                ZoneLedgerEntry(
                    nodeId = item.optString("nodeId", ""),
                    zoneId = item.optString("zoneId", ""),
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
