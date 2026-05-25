package com.ghalbitnet.meshx2.vpn

import android.content.Context
import org.json.JSONObject

object GatewaySessionManager {

    private const val PREFS_NAME = "ghalbit_gateway_sessions"
    private const val KEY_SESSIONS = "sessions"
    private const val SESSION_TTL_MS = 120_000L
    private const val PACKET_TTL_MS = 60_000L

    enum class SessionCheck {
        ACCEPTED,
        DUPLICATE_PACKET,
        SESSION_EXPIRED
    }

    @Synchronized
    fun validatePacket(
        context: Context,
        clientNodeId: String,
        sessionId: String,
        packetId: String,
        timestamp: Long
    ): SessionCheck {
        val now = System.currentTimeMillis()
        val root = load(context, now)
        val sessionKey = "$clientNodeId|$sessionId"
        val session = root.optJSONObject(sessionKey) ?: JSONObject()
        val lastSeen = session.optLong("lastSeen", 0L)
        if (lastSeen > 0L && now - lastSeen > SESSION_TTL_MS) {
            root.remove(sessionKey)
            save(context, root)
            return SessionCheck.SESSION_EXPIRED
        }

        val packetMap = session.optJSONObject("packets") ?: JSONObject()
        if (packetId.isNotBlank() && packetMap.has(packetId)) {
            val seenAt = packetMap.optLong(packetId, 0L)
            if (now - seenAt <= PACKET_TTL_MS) {
                return SessionCheck.DUPLICATE_PACKET
            }
        }

        val cleanedPackets = JSONObject()
        val keys = packetMap.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val seenAt = packetMap.optLong(key, 0L)
            if (now - seenAt <= PACKET_TTL_MS) {
                cleanedPackets.put(key, seenAt)
            }
        }
        if (packetId.isNotBlank()) {
            cleanedPackets.put(packetId, now)
        }
        root.put(
            sessionKey,
            JSONObject()
                .put("clientNodeId", clientNodeId)
                .put("sessionId", sessionId)
                .put("createdAt", session.optLong("createdAt", now.coerceAtMost(timestamp.takeIf { it > 0L } ?: now)))
                .put("lastSeen", now)
                .put("packets", cleanedPackets)
        )
        save(context, root)
        return SessionCheck.ACCEPTED
    }

    @Synchronized
    fun cleanup(context: Context) {
        save(context, load(context, System.currentTimeMillis()))
    }

    private fun load(
        context: Context,
        now: Long
    ): JSONObject {
        val raw =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SESSIONS, "{}")
                .orEmpty()
        val source = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        val cleaned = JSONObject()
        val keys = source.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val session = source.optJSONObject(key) ?: continue
            val lastSeen = session.optLong("lastSeen", 0L)
            if (now - lastSeen <= SESSION_TTL_MS) {
                cleaned.put(key, session)
            }
        }
        return cleaned
    }

    private fun save(
        context: Context,
        source: JSONObject
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SESSIONS, source.toString())
            .apply()
    }
}
