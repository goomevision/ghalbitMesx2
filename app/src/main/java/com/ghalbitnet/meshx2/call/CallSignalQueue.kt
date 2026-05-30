package com.ghalbitnet.meshx2.call

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class CallSignalDispatchResult(
    val delivered: Boolean,
    val finalState: String,
    val statusMessage: String,
    val routeType: VoipRouteType? = null
)

object CallSignalQueue {
    private const val PREFS = "ghalbit_call_signal_queue"
    private const val KEY_EVENTS = "events"
    private val backoffSteps = longArrayOf(5_000L, 15_000L, 30_000L, 60_000L, 5 * 60_000L)

    fun enqueue(context: Context, event: CallSignalEvent) {
        val events = load(context).filterNot { it.callId == event.callId && it.type == event.type }
        save(context, events + event)
        Log.d("GHALBIT-CALL-SIGNAL", "queued type=${event.type}")
    }

    fun remove(context: Context, eventId: String) {
        save(context, load(context).filterNot { it.eventId == eventId })
    }

    suspend fun dispatchNow(context: Context, event: CallSignalEvent): CallSignalDispatchResult =
        withContext(Dispatchers.IO) {
            val result = GhalbitCallManager.dispatchSignalEvent(context, event)
            if (result.delivered) {
                remove(context, event.eventId)
            } else if (result.finalState != "FAILED_FINAL") {
                val nextAttempt = event.attempts + 1
                val nextRetryAt = System.currentTimeMillis() + backoffSteps.getOrElse(event.attempts) { backoffSteps.last() }
                save(
                    context,
                    load(context).map {
                        if (it.eventId == event.eventId) {
                            it.copy(attempts = nextAttempt, nextRetryAt = nextRetryAt)
                        } else {
                            it
                        }
                    }
                )
            }
            result
        }

    suspend fun flushDue(context: Context): Int = withContext(Dispatchers.IO) {
        val due = load(context).filter { it.nextRetryAt <= System.currentTimeMillis() }
        due.forEach { dispatchNow(context, it) }
        due.size
    }

    private fun load(context: Context): List<CallSignalEvent> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_EVENTS, "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    CallSignalEvent(
                        eventId = item.optString("eventId"),
                        callId = item.optString("callId"),
                        type = item.optString("type"),
                        peerName = item.optString("peerName"),
                        nodeId = item.optString("nodeId"),
                        globalId = item.optString("globalId").takeIf { it.isNotBlank() },
                        publicKey = item.optString("publicKey").takeIf { it.isNotBlank() },
                        publicKeyHash = item.optString("publicKeyHash").takeIf { it.isNotBlank() },
                        walletAddress = item.optString("walletAddress").takeIf { it.isNotBlank() },
                        displayName = item.optString("displayName").takeIf { it.isNotBlank() },
                        routeHint = item.optString("routeHint").takeIf { it.isNotBlank() },
                        transportIp = item.optString("transportIp").takeIf { it.isNotBlank() },
                        localNodeId = item.optString("localNodeId"),
                        localGlobalId = item.optString("localGlobalId").takeIf { it.isNotBlank() },
                        localPublicKeyHash = item.optString("localPublicKeyHash").takeIf { it.isNotBlank() },
                        createdAt = item.optLong("createdAt"),
                        attempts = item.optInt("attempts"),
                        nextRetryAt = item.optLong("nextRetryAt")
                    )
                )
            }
        }
    }

    private fun save(context: Context, events: List<CallSignalEvent>) {
        val array =
            JSONArray().apply {
                events.forEach { event ->
                    put(
                        JSONObject()
                            .put("eventId", event.eventId)
                            .put("callId", event.callId)
                            .put("type", event.type)
                            .put("peerName", event.peerName)
                            .put("nodeId", event.nodeId)
                            .put("globalId", event.globalId)
                            .put("publicKey", event.publicKey)
                            .put("publicKeyHash", event.publicKeyHash)
                            .put("walletAddress", event.walletAddress)
                            .put("displayName", event.displayName)
                            .put("routeHint", event.routeHint)
                            .put("transportIp", event.transportIp)
                            .put("localNodeId", event.localNodeId)
                            .put("localGlobalId", event.localGlobalId)
                            .put("localPublicKeyHash", event.localPublicKeyHash)
                            .put("createdAt", event.createdAt)
                            .put("attempts", event.attempts)
                            .put("nextRetryAt", event.nextRetryAt)
                    )
                }
            }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EVENTS, array.toString())
            .apply()
    }
}
