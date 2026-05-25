package com.ghalbitnet.meshx2.core.network

import android.content.Context
import com.ghalbitnet.meshx2.vpn.VpnLogManager
import org.json.JSONObject

object PeerAddressRegistry {

    private const val PREFS_NAME = "ghalbit_peer_address_registry"
    private const val KEY_ENTRIES = "entries"
    const val DEFAULT_MESH_SOCKET_PORT: Int = 56565
    private const val MAX_AGE_MS = 60_000L
    private const val IDENTICAL_REGISTER_DEBOUNCE_MS = 30_000L

    data class Entry(
        val peerId: String,
        val address: String,
        val port: Int,
        val lastSeen: Long
    )

    fun register(
        context: Context,
        peerId: String,
        address: String,
        port: Int = DEFAULT_MESH_SOCKET_PORT
    ) {
        if (peerId.isBlank() || address.isBlank()) return
        val now = System.currentTimeMillis()
        val json = load(context, now)
        val existing = json.optJSONObject(peerId)
        if (existing != null) {
            val existingAddress = existing.optString("address").trim()
            val existingPort = existing.optInt("port", DEFAULT_MESH_SOCKET_PORT)
            val existingLastSeen = existing.optLong("lastSeen", 0L)
            if (
                existingAddress == address &&
                existingPort == port &&
                now - existingLastSeen < IDENTICAL_REGISTER_DEBOUNCE_MS
            ) {
                return
            }
        }
        json.put(
            peerId,
            JSONObject()
                .put("address", address)
                .put("port", port)
                .put("lastSeen", now)
        )
        save(context, json)
        VpnLogManager.info(
            "PEER_ADDRESS_REGISTERED",
            "peerId=$peerId address=$address port=$port"
        )
    }

    fun resolve(
        context: Context,
        peerId: String
    ): Entry? {
        val json = load(context, System.currentTimeMillis())
        val item = json.optJSONObject(peerId) ?: return null
        val address = item.optString("address").trim()
        val port = item.optInt("port", DEFAULT_MESH_SOCKET_PORT)
        val lastSeen = item.optLong("lastSeen", 0L)
        if (address.isBlank()) return null
        return Entry(peerId, address, port, lastSeen)
    }

    private fun load(
        context: Context,
        now: Long
    ): JSONObject {
        val raw =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ENTRIES, "{}")
                .orEmpty()
        val json = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        val cleaned = JSONObject()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val item = json.optJSONObject(key) ?: continue
            val lastSeen = item.optLong("lastSeen", 0L)
            if (now - lastSeen <= MAX_AGE_MS) {
                cleaned.put(key, item)
            }
        }
        save(context, cleaned)
        return cleaned
    }

    private fun save(
        context: Context,
        json: JSONObject
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENTRIES, json.toString())
            .apply()
    }
}
