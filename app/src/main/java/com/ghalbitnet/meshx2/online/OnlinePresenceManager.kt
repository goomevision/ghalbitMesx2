package com.ghalbitnet.meshx2.online

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.ghalbitnet.meshx2.BuildConfig
import com.ghalbitnet.meshx2.chat.ChatDeliveryManager
import com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager
import com.ghalbitnet.meshx2.security.NodeSigningIdentityManager
import com.ghalbitnet.meshx2.util.LogThrottle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object OnlinePresenceManager : PresenceApi {
    private const val PREFS = "ghalbit_online_presence"
    private const val KEY_ITEMS = "items"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var currentContext: Context? = null

    @Volatile
    private var syncStarted = false

    fun bind(context: Context) {
        currentContext = context.applicationContext
        if (syncStarted) return
        syncStarted = true
        scope.launch {
            while (true) {
                val snapshot = PowerAwareSyncManager.snapshot(context.applicationContext)
                runCatching { sendAuthoritativeHeartbeat() }
                    .onFailure { Log.e("GHALBIT-PRESENCE-CLIENT", "heartbeat loop failed", it) }
                if (snapshot.lowPowerMode) {
                    Log.d("GHALBIT-POWER", "heartbeat throttled")
                }
                delay(snapshot.heartbeatIntervalMs)
            }
        }
    }

    fun all(context: Context): List<OnlinePresence> {
        val raw = prefs(context).getString(KEY_ITEMS, "[]").orEmpty()
        val array = JSONArray(raw)
        val items = mutableListOf<OnlinePresence>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val globalId = item.optString("globalId")
            if (globalId.isBlank()) continue
            val relayUrl = item.optString("relayUrl").ifBlank { effectivePresenceBaseUrl() }
            items +=
                OnlinePresence(
                    nodeId = item.optString("nodeId"),
                    globalId = globalId,
                    publicKeyHash = item.optString("publicKeyHash").ifBlank { null },
                    online = item.optBoolean("online", true),
                    route = relayUrl.takeIf { it.isNotBlank() }?.let { InternetRoute(globalId, relayUrl) },
                    lastSeen = item.optLong("lastSeen", System.currentTimeMillis())
                )
        }
        return items
    }

    override suspend fun registerOnline(presence: OnlinePresence): Boolean {
        val result = heartbeat(presence)
        return result.online
    }

    suspend fun registerOnline(context: Context, nodeId: String, globalId: String, publicKeyHash: String?): Boolean {
        bind(context)
        val presence = OnlinePresence(nodeId = nodeId, globalId = globalId, publicKeyHash = publicKeyHash, online = true, route = configuredRoute(globalId))
        val result = heartbeat(presence)
        if (!result.online) {
            cachePresence(context, presence.copy(online = false))
        }
        return result.online
    }

    override suspend fun updatePresence(presence: OnlinePresence): Boolean = registerOnline(presence)

    suspend fun updatePresence(context: Context, nodeId: String, globalId: String, publicKeyHash: String?): Boolean {
        return registerOnline(context, nodeId, globalId, publicKeyHash)
    }

    override suspend fun checkPeerOnline(targetGlobalId: String): OnlinePresence? {
        return withContext(Dispatchers.IO) {
            currentContext?.let { checkPeerOnline(it, targetGlobalId) }
        }
    }

    suspend fun checkPeerOnline(context: Context, targetGlobalId: String): OnlinePresence? {
        bind(context)
        if (!BuildConfig.INTERNET_RELAY_CONFIGURED) {
            LogThrottle.w("GHALBIT-ANDROID-RELAY", "relay-missing:checkPeerOnline", "missing config", 10_000L, context)
            Log.w("GHALBIT-PRESENCE-CLIENT", "stale cache used")
            return all(context).firstOrNull { it.globalId == targetGlobalId && it.online }
        }
        val response = getJson("${effectivePresenceBaseUrl().trimEnd('/')}/presence/$targetGlobalId")
        return if (response != null && response.optBoolean("ok")) {
            val presenceJson = response.optJSONObject("presence")
            val authoritativeOnline = presenceJson?.optBoolean("online", true) ?: response.optBoolean("online", true)
            val authoritativeLastSeen = presenceJson?.optLong("lastSeen", 0L)?.takeIf { it > 0L }
                ?: response.optLong("lastSeen", 0L).takeIf { it > 0L }
            val presence =
                OnlinePresence(
                    nodeId = presenceJson?.optString("nodeId").orEmpty(),
                    globalId = targetGlobalId,
                    publicKeyHash = presenceJson?.optString("publicKeyHash").takeIf { !it.isNullOrBlank() },
                    online = authoritativeOnline,
                    route = configuredRoute(targetGlobalId),
                    lastSeen = authoritativeLastSeen ?: System.currentTimeMillis()
                )
            cachePresence(context, presence)
            if (presence.online) {
                triggerPendingRetryForOnlinePeer(context, presence)
                Log.d("GHALBIT-PRESENCE-CLIENT", "peer online globalId=$targetGlobalId authoritative=true")
                presence
            } else {
                Log.d("GHALBIT-PRESENCE-CLIENT", "peer offline globalId=$targetGlobalId authoritative=true")
                null
            }
        } else {
            Log.d("GHALBIT-PRESENCE-CLIENT", "peer offline globalId=$targetGlobalId")
            all(context).firstOrNull { it.globalId == targetGlobalId }?.also {
                cachePresence(context, it.copy(online = false))
            }
            null
        }
    }

    override suspend fun heartbeat(presence: OnlinePresence): RemotePresenceResult {
        val context = currentContext ?: return RemotePresenceResult(false, "UNKNOWN_REMOTE", error = "missing_context")
        if (!BuildConfig.INTERNET_RELAY_CONFIGURED) {
            LogThrottle.w("GHALBIT-ANDROID-RELAY", "relay-missing:heartbeat", "missing config", 10_000L, context)
            cachePresence(context, presence.copy(online = false))
            return RemotePresenceResult(false, "INTERNET_RELAY_NOT_CONFIGURED", presence.copy(online = false), "missing_config")
        }
        val createdAt = System.currentTimeMillis()
        val signingIdentity = NodeSigningIdentityManager.getOrCreate(context)
        val payloadCore =
            RelaySecurityProof.Payload(
                senderGlobalId = signingIdentity.globalId,
                targetGlobalId = presence.globalId,
                messageId = "presence-${presence.globalId}-$createdAt",
                packetId = "presence-${presence.globalId}-$createdAt",
                createdAt = createdAt,
                expiresAt = createdAt + PowerAwareSyncManager.snapshot(context).heartbeatIntervalMs * 2,
                nonce = RelaySecurityProof.nonce(),
                contentType = "PRESENCE",
                payload = "heartbeat",
                senderPublicKey = signingIdentity.publicKeyBase64
            )
        val payload =
            JSONObject()
                .put("globalId", presence.globalId)
                .put("nodeId", signingIdentity.nodeId)
                .put("publicKeyHash", signingIdentity.publicKeyHash)
                .put("senderGlobalId", signingIdentity.globalId)
                .put("senderNodeId", signingIdentity.nodeId)
                .put("senderPublicKey", signingIdentity.publicKeyBase64)
                .put("algorithm", payloadCore.algorithm)
                .put("messageId", payloadCore.messageId)
                .put("packetId", payloadCore.packetId)
                .put("createdAt", payloadCore.createdAt)
                .put("expiresAt", payloadCore.expiresAt)
                .put("nonce", payloadCore.nonce)
                .put("contentType", payloadCore.contentType)
                .put("payload", payloadCore.payload)
                .put("signature", NodeSigningIdentityManager.sign(context, RelaySecurityProof.canonical(payloadCore), payloadCore.messageId))
                .put("routeHint", "")
                .put("appVersion", "1.0.1")
                .put("networkType", currentNetworkType(context))
                .put("deviceCapability", "ANDROID")
        val response = postJson("${effectivePresenceBaseUrl().trimEnd('/')}/presence/heartbeat", payload)
        return if (response?.optBoolean("ok") == true) {
            val serverStatus = response.optString("status", "ONLINE_REMOTE")
            val serverOnline = response.optBoolean("online", true) && !serverStatus.contains("OFFLINE", ignoreCase = true)
            val serverLastSeen = response.optLong("lastSeen", 0L).takeIf { it > 0L } ?: System.currentTimeMillis()
            val updated = presence.copy(
                online = serverOnline,
                route = configuredRoute(presence.globalId),
                lastSeen = serverLastSeen
            )
            cachePresence(context, updated)
            Log.d(
                "GHALBIT-PRESENCE-CLIENT",
                "heartbeat authoritative globalId=${presence.globalId} status=$serverStatus online=$serverOnline lastSeen=$serverLastSeen"
            )
            RemotePresenceResult(serverOnline, serverStatus, updated)
        } else {
            cachePresence(context, presence.copy(online = false))
            Log.w("GHALBIT-PRESENCE-CLIENT", "heartbeat failed globalId=${presence.globalId}")
            RemotePresenceResult(false, response?.optString("status", "OFFLINE_REMOTE") ?: "OFFLINE_REMOTE", presence.copy(online = false), response?.optString("error"))
        }
    }

    fun getOnlineRoute(context: Context, targetGlobalId: String): InternetRoute? {
        return all(context).firstOrNull { it.globalId == targetGlobalId && it.online }?.route
            ?: configuredRoute(targetGlobalId)
    }

    fun applyRealtimePresence(context: Context, presence: OnlinePresence) {
        val appContext = context.applicationContext
        cachePresence(appContext, presence)
        if (presence.online) {
            Log.d("GHALBIT-PRESENCE-CLIENT", "peer online globalId=${presence.globalId}")
            triggerPendingRetryForOnlinePeer(appContext, presence)
        } else {
            Log.d("GHALBIT-PRESENCE-CLIENT", "peer offline globalId=${presence.globalId}")
        }
    }

    fun hasInternet(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun sendAuthoritativeHeartbeat() {
        val context = currentContext ?: return
        if (!hasInternet(context)) {
            Log.w("GHALBIT-PRESENCE-CLIENT", "heartbeat failed no internet")
            return
        }
        val globalId = MeshRuntimeManager.localGlobalId()
        val nodeId = MeshRuntimeManager.localNodeId()
        val publicKeyHash = MeshRuntimeManager.localPublicKeyHash()
        if (globalId.isBlank() || nodeId.isBlank()) return
        registerOnline(context, nodeId, globalId, publicKeyHash)
    }

    private fun triggerPendingRetryForOnlinePeer(context: Context, presence: OnlinePresence) {
        val now = System.currentTimeMillis()
        val targets = PendingMessageStore.all(context)
            .filter { pending ->
                pending.targetGlobalId == presence.globalId ||
                    pending.targetNodeId == presence.nodeId ||
                    pending.chatId == presence.nodeId
            }
            .filter { pending -> pending.expiresAt <= 0L || now <= pending.expiresAt }
        if (targets.isEmpty()) return
        val chatIds = targets.map { it.chatId }.distinct()
        targets.forEach { pending ->
            PendingMessageStore.upsert(
                context,
                pending.copy(
                    nextRetryAt = now,
                    lastFailureReason = "peerOnlineRetry"
                )
            )
        }
        Log.d(
            "GHALBIT-DELIVERY-PENDING",
            "peerOnlineRetry globalId=${presence.globalId} nodeId=${presence.nodeId} count=${targets.size} chats=${chatIds.joinToString(",")}" 
        )
        chatIds.forEach { chatId -> ChatDeliveryManager.retryPendingForChat(context, chatId) }
    }

    private fun configuredRoute(globalId: String): InternetRoute? {
        val relayUrl = effectiveRelayBaseUrl()
        return relayUrl.takeIf { it.isNotBlank() }?.let { InternetRoute(globalId, it) }
    }

    private fun cachePresence(context: Context, presence: OnlinePresence) {
        val items = all(context).associateBy { it.globalId }.toMutableMap()
        items[presence.globalId] = presence.copy(lastSeen = System.currentTimeMillis())
        save(context, items.values.sortedByDescending { it.lastSeen })
        Log.d("GHALBIT-ONLINE", "register node=${presence.nodeId} globalId=${presence.globalId} relay=${presence.route?.relayUrl ?: "-"}")
    }

    private fun save(context: Context, items: List<OnlinePresence>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("nodeId", item.nodeId)
                    .put("globalId", item.globalId)
                    .put("publicKeyHash", item.publicKeyHash)
                    .put("online", item.online)
                    .put("relayUrl", item.route?.relayUrl ?: "")
                    .put("lastSeen", item.lastSeen)
            )
        }
        prefs(context).edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private suspend fun postJson(urlValue: String, payload: JSONObject): JSONObject? =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(urlValue).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5000
                    readTimeout = 5000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                connection.outputStream.bufferedWriter().use { it.write(payload.toString()) }
                val stream =
                    if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                if (text.isBlank()) JSONObject().put("ok", connection.responseCode in 200..299) else JSONObject(text)
            }.getOrNull()
        }

    private suspend fun getJson(urlValue: String): JSONObject? =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(urlValue).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                val stream =
                    if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                if (text.isBlank()) null else JSONObject(text)
            }.getOrNull()
        }

    private fun effectiveRelayBaseUrl(): String = BuildConfig.BASE_RELAY_URL.trim()

    private fun effectivePresenceBaseUrl(): String {
        val presence = BuildConfig.BASE_PRESENCE_URL.trim()
        return if (presence.isNotBlank()) presence else BuildConfig.BASE_RELAY_URL.trim()
    }

    private fun currentNetworkType(context: Context): String {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "UNKNOWN"
        val network = manager.activeNetwork ?: return "OFFLINE"
        val caps = manager.getNetworkCapabilities(network) ?: return "UNKNOWN"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            else -> "OTHER"
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
