package com.ghalbitnet.meshx2.online

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object PreparedRouteManager {
    private const val PREFS = "ghalbit_prepared_routes"
    private const val KEY_ITEMS = "items"
    private const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L

    suspend fun prepareForActivePeer(
        context: Context,
        peerGlobalId: String,
        primaryRoute: String,
        preferredSecondary: String = "INTERNET_RELAY_PREPARED"
    ): PreparedRouteCandidate = withContext(Dispatchers.IO) {
        expireIdle(context)
        val current = currentForPeer(context, peerGlobalId)
        if (current != null && current.expiresAt > System.currentTimeMillis()) {
            return@withContext current
        }
        val candidate =
            PreparedRouteCandidate(
                sessionId = "SESSION-${UUID.randomUUID().toString().take(8)}",
                peerGlobalId = peerGlobalId,
                primaryRoute = primaryRoute,
                secondaryRoute = preferredSecondary,
                state = if (OnlineFallbackTransport.isConfigured()) SecondaryRouteState.INTERNET_RELAY_PREPARED else SecondaryRouteState.MESH_ONLY
            )
        save(context, candidate)
        Log.d("GHALBIT-SECONDARY-ROUTE", "prepared peer=$peerGlobalId")
        Log.d("GHALBIT-SECONDARY-POWER", "warm relay allowed")
        candidate
    }

    suspend fun requestSecondaryRoute(
        context: Context,
        sessionId: String,
        peerGlobalId: String,
        primaryRoute: String
    ): PreparedRouteCandidate = withContext(Dispatchers.IO) {
        val prepared = prepareForActivePeer(context, peerGlobalId, primaryRoute)
        val response = OnlineFallbackTransport.prepareRouteSession(context, sessionId, peerGlobalId, primaryRoute)
        val merged =
            prepared.copy(
                relaySessionId = response.relaySessionId ?: prepared.relaySessionId,
                relayUrl = response.relayUrl ?: prepared.relayUrl,
                routeToken = response.routeToken ?: prepared.routeToken,
                expiresAt = response.expiresAt.takeIf { it > 0L } ?: prepared.expiresAt,
                state = if (response.ready) SecondaryRouteState.SECONDARY_READY else SecondaryRouteState.SERVER_SESSION_RELAY,
                ready = response.ready,
                healthScore = response.healthScore
            )
        save(context, merged)
        if (!merged.relaySessionId.isNullOrBlank()) {
            Log.d("GHALBIT-ROUTE-COORD", "server returned relaySessionId=${merged.relaySessionId}")
        }
        if (merged.ready) {
            Log.d("GHALBIT-SECONDARY-ROUTE", "selected")
            RelayRealtimeChannel.warmRelay(context, peerGlobalId)
        }
        merged
    }

    suspend fun validateSecondaryRoute(
        context: Context,
        candidate: PreparedRouteCandidate
    ): PreparedRouteCandidate = withContext(Dispatchers.IO) {
        val validation = OnlineFallbackTransport.validatePreparedRoute(context, candidate)
        val next =
            candidate.copy(
                lastValidatedAt = System.currentTimeMillis(),
                ready = validation.ready,
                state = if (validation.ready) SecondaryRouteState.SECONDARY_READY else candidate.state,
                healthScore = validation.healthScore,
                relaySessionId = validation.relaySessionId ?: candidate.relaySessionId
            )
        save(context, next)
        if (validation.ready) {
            Log.d("GHALBIT-SECONDARY-ROUTE", "validated relaySessionId=${next.relaySessionId}")
            Log.d("GHALBIT-ROUTE-COORD", "client validation ok")
            Log.d("GHALBIT-ROUTE-COORD", "secondary ready")
            Log.d("GHALBIT-ROUTE-UI", "secondary ready shown")
        }
        next
    }

    suspend fun currentForPeer(context: Context, peerGlobalId: String): PreparedRouteCandidate? =
        withContext(Dispatchers.IO) {
            load(context).firstOrNull { it.peerGlobalId == peerGlobalId && it.expiresAt > System.currentTimeMillis() }
        }

    suspend fun expireIdle(context: Context) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val remaining = load(context).filter { it.expiresAt > now && now - it.preparedAt <= IDLE_TIMEOUT_MS }
        if (remaining.size != load(context).size) {
            persist(context, remaining)
            Log.d("GHALBIT-SECONDARY-ROUTE", "expired")
            Log.d("GHALBIT-SECONDARY-POWER", "idle closed")
            RelayRealtimeChannel.closeWarmRelay()
        }
    }

    fun statusLabel(candidate: PreparedRouteCandidate?): String {
        return when {
            candidate == null && !OnlineFallbackTransport.isConfigured() -> "Relay belum diatur"
            candidate == null -> "Mesh aktif"
            candidate.ready -> "Relay cadangan siap"
            candidate.state == SecondaryRouteState.SERVER_SESSION_RELAY -> "Jalur kedua siap"
            else -> "Mesh aktif"
        }
    }

    private fun save(context: Context, candidate: PreparedRouteCandidate) {
        val next = load(context).filterNot { it.peerGlobalId == candidate.peerGlobalId } + candidate
        persist(context, next)
    }

    private fun load(context: Context): List<PreparedRouteCandidate> {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    PreparedRouteCandidate(
                        sessionId = item.optString("sessionId"),
                        peerGlobalId = item.optString("peerGlobalId"),
                        primaryRoute = item.optString("primaryRoute"),
                        secondaryRoute = item.optString("secondaryRoute"),
                        relaySessionId = item.optString("relaySessionId").ifBlank { null },
                        relayUrl = item.optString("relayUrl").ifBlank { null },
                        routeToken = item.optString("routeToken").ifBlank { null },
                        preparedAt = item.optLong("preparedAt"),
                        expiresAt = item.optLong("expiresAt"),
                        healthScore = item.optInt("healthScore", 0),
                        lastValidatedAt = item.optLong("lastValidatedAt", 0L),
                        ready = item.optBoolean("ready"),
                        state = SecondaryRouteState.entries.firstOrNull { it.name == item.optString("state") } ?: SecondaryRouteState.MESH_ONLY
                    )
                )
            }
        }
    }

    private fun persist(context: Context, items: List<PreparedRouteCandidate>) {
        val array =
            JSONArray().apply {
                items.forEach { item ->
                    put(
                        JSONObject()
                            .put("sessionId", item.sessionId)
                            .put("peerGlobalId", item.peerGlobalId)
                            .put("primaryRoute", item.primaryRoute)
                            .put("secondaryRoute", item.secondaryRoute)
                            .put("relaySessionId", item.relaySessionId)
                            .put("relayUrl", item.relayUrl)
                            .put("routeToken", item.routeToken)
                            .put("preparedAt", item.preparedAt)
                            .put("expiresAt", item.expiresAt)
                            .put("healthScore", item.healthScore)
                            .put("lastValidatedAt", item.lastValidatedAt)
                            .put("ready", item.ready)
                            .put("state", item.state.name)
                    )
                }
            }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, array.toString())
            .apply()
    }
}
