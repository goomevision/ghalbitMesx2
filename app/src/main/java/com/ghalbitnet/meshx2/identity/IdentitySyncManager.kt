package com.ghalbitnet.meshx2.identity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.provider.Settings
import android.util.Log
import com.ghalbitnet.meshx2.core.node.NodeStatusManager
import com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager
import com.ghalbitnet.meshx2.identity.IdentityCopyLimiter.MAX_ACTIVE_COPIES
import com.ghalbitnet.meshx2.model.MeshNode
import com.ghalbitnet.meshx2.online.OnlineFallbackTransport
import com.ghalbitnet.meshx2.online.OnlinePresenceManager
import com.ghalbitnet.meshx2.routing.IntelligentRouteMemory
import com.ghalbitnet.meshx2.security.NodeSigningIdentityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

object IdentitySyncManager {
    private const val PREFS = "ghalbit_identity_sync"
    private const val KEY_LOCAL_RECORD = "local_record"
    private const val KEY_MESH_COPIES = "mesh_copies"
    private const val KEY_LAST_SYNC_ATTEMPT = "last_sync_attempt"
    private const val KEY_LAST_SYNC_ERROR = "last_sync_error"
    private const val SYNC_BACKOFF_MS = 2 * 60 * 1000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = AtomicBoolean(false)

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        if (!initialized.compareAndSet(false, true)) return
        registerLocalIdentity(appContext)
        registerNetworkWatcher(appContext)
    }

    fun currentIdentity(context: Context): CallIdentityRecord? =
        context.applicationContext.prefs().getString(KEY_LOCAL_RECORD, null)
            ?.takeIf { it.isNotBlank() }
            ?.let(::recordFromJson)

    fun registerLocalIdentity(
        context: Context,
        displayName: String = MeshRuntimeManager.localNodeId().ifBlank {
            NodeSigningIdentityManager.getOrCreate(context).nodeId
        }
    ): CallIdentityRecord {
        val appContext = context.applicationContext
        val signing = NodeSigningIdentityManager.getOrCreate(appContext)
        val existing = currentIdentity(appContext)
        val now = System.currentTimeMillis()
        val record =
            CallIdentityRecord(
                callId = signing.globalId,
                userDisplayName = displayName.ifBlank { signing.nodeId },
                publicKey = signing.publicKeyBase64,
                deviceId = hashedDeviceId(appContext, signing.nodeId),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                syncState = existing?.syncState ?: IdentitySyncState.LOCAL_ONLY,
                lastServerSyncAt = existing?.lastServerSyncAt ?: 0L,
                lastMeshBroadcastAt = existing?.lastMeshBroadcastAt ?: 0L,
                copyVersion = (existing?.copyVersion ?: 0) + 1,
                signature = existing?.signature
            )
        persistRecord(appContext, record)
        IdentityRegistry.upsert(
            GhalbitIdentityRecord(
                globalId = record.callId,
                publicKey = record.publicKey,
                walletAddress = null,
                displayName = record.userDisplayName,
                lastKnownIp = null,
                lastSeen = now,
                trustScore = 100,
                relayCapable = OnlineFallbackTransport.isConfigured(),
                gatewayCapable = OnlinePresenceManager.hasInternet(appContext)
            )
        )
        if (canAttemptServerSync(appContext)) {
            scope.launch { syncToServerIfOnline(appContext, IdentitySyncTrigger.REGISTRATION) }
        } else {
            markPendingIfOffline(appContext)
            prepareMeshCopiesIfNeeded(appContext, record)
        }
        return record
    }

    suspend fun syncToServerIfOnline(
        context: Context,
        trigger: IdentitySyncTrigger = IdentitySyncTrigger.MANUAL_REFRESH
    ): CallIdentityRecord? {
        val appContext = context.applicationContext
        val record = currentIdentity(appContext) ?: return null
        if (!canAttemptServerSync(appContext)) {
            markPendingIfOffline(appContext)
            return currentIdentity(appContext)
        }
        val now = System.currentTimeMillis()
        val prefs = appContext.prefs()
        val lastAttempt = prefs.getLong(KEY_LAST_SYNC_ATTEMPT, 0L)
        if (now - lastAttempt < SYNC_BACKOFF_MS && record.syncState == IdentitySyncState.PENDING_SERVER_SYNC) {
            return record
        }
        prefs.edit().putLong(KEY_LAST_SYNC_ATTEMPT, now).apply()
        val routeHint = preferredRouteHint(appContext, record.callId)
        val response =
            if (record.lastServerSyncAt == 0L) {
                IdentityServerClient.registerIdentity(appContext, record, routeHint)
            } else {
                IdentityServerClient.syncIdentity(appContext, record, routeHint)
            }
        return if (response.ok) {
            handleServerAck(appContext, response)
        } else {
            if (response.conflict) {
                handleServerConflict(appContext)
            } else {
                prefs.edit().putString(KEY_LAST_SYNC_ERROR, response.error ?: "unknown").apply()
                markPendingIfOffline(appContext)
            }
            currentIdentity(appContext)
        }.also {
            Log.d("GHALBIT-IDENTITY-SYNC", "trigger=$trigger state=${it?.syncState}")
        }
    }

    fun markPendingIfOffline(context: Context): CallIdentityRecord? {
        val appContext = context.applicationContext
        val record = currentIdentity(appContext) ?: return null
        val next =
            record.copy(
                updatedAt = System.currentTimeMillis(),
                syncState = IdentitySyncState.PENDING_SERVER_SYNC
            )
        persistRecord(appContext, next)
        prepareMeshCopiesIfNeeded(appContext, next)
        return next
    }

    fun retryPendingWhenInternetReturns(context: Context) {
        scope.launch {
            syncToServerIfOnline(context.applicationContext, IdentitySyncTrigger.NETWORK_RETURNED)
        }
    }

    fun handleServerAck(context: Context, response: IdentitySyncResponse): CallIdentityRecord? {
        val appContext = context.applicationContext
        val record = currentIdentity(appContext) ?: return null
        val now = response.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        val next =
            record.copy(
                updatedAt = now,
                syncState = IdentitySyncState.SERVER_SYNCED,
                lastServerSyncAt = now
            )
        persistRecord(appContext, next)
        expireAllCopies(appContext, record.callId)
        preferredRouteHint(appContext, record.callId)?.let { routeHint ->
            scope.launch { IdentityServerClient.sendRouteHint(appContext, record.callId, routeHint) }
        }
        return next
    }

    fun handleServerConflict(context: Context): CallIdentityRecord? {
        val appContext = context.applicationContext
        val record = currentIdentity(appContext) ?: return null
        val next =
            record.copy(
                updatedAt = System.currentTimeMillis(),
                syncState = IdentitySyncState.CONFLICT_NEEDS_RESOLVE
            )
        persistRecord(appContext, next)
        return next
    }

    fun activeMeshCopies(context: Context, callId: String): List<IdentityMeshCopy> {
        val appContext = context.applicationContext
        val copies = IdentityCopyLimiter.pruneExpiredCopies(loadCopies(appContext))
        persistCopies(appContext, copies)
        return copies.filter { it.callId == callId && !it.isExpired() }
    }

    fun findBestCopyRouteHint(context: Context, callId: String): String? =
        activeMeshCopies(context, callId)
            .sortedWith(compareByDescending<IdentityMeshCopy> { it.routeScore }.thenBy { it.hopCount })
            .firstOrNull()
            ?.routeHint

    fun notifyCopyReachedInternet(context: Context, copyId: String) {
        val appContext = context.applicationContext
        val copies =
            loadCopies(appContext).map {
                if (it.copyId == copyId) {
                    it.copy(hasReachedInternet = true)
                } else {
                    it
                }
            }
        persistCopies(appContext, copies)
        val reached = copies.firstOrNull { it.copyId == copyId } ?: return
        val record = currentIdentity(appContext) ?: return
        scope.launch {
            IdentityServerClient.notifyCopyReachedInternet(appContext, record, reached, reached.routeHint)
            syncToServerIfOnline(appContext, IdentitySyncTrigger.MESH_COPY_REACHED_INTERNET)
        }
    }

    private fun prepareMeshCopiesIfNeeded(context: Context, record: CallIdentityRecord) {
        if (OnlinePresenceManager.hasInternet(context)) return
        val availableNodes =
            NodeStatusManager.getOnlineNodes()
                .filter { it.online && it.ipAddress.isNotBlank() }
                .sortedByDescending(::nodeRouteScore)
        if (availableNodes.isEmpty()) return
        val existing = IdentityCopyLimiter.pruneExpiredCopies(loadCopies(context)).toMutableList()
        var next = existing.toList()
        var created = false
        availableNodes.take(3).forEach { node ->
            if (!IdentityCopyLimiter.canCreateCopy(record.callId, next)) return@forEach
            if (next.any { it.callId == record.callId && it.routeHint == node.ipAddress && !it.isExpired() }) return@forEach
            val copy =
                IdentityMeshCopy(
                    callId = record.callId,
                    ownerDeviceId = record.deviceId,
                    copyId = "copy-${UUID.randomUUID().toString().take(8)}",
                    copyIndex = IdentityCopyLimiter.getActiveCopyCount(record.callId, next) + 1,
                    maxCopies = MAX_ACTIVE_COPIES,
                    hopCount = 1,
                    ttl = IdentityMeshCopy.DEFAULT_TTL_MS,
                    createdAt = System.currentTimeMillis(),
                    lastForwardedAt = System.currentTimeMillis(),
                    hasReachedInternet = false,
                    routeHint = node.ipAddress,
                    routeScore = nodeRouteScore(node)
                )
            next = IdentityCopyLimiter.registerCopy(copy, next)
            created = true
            Log.d("GHALBIT-IDENTITY-COPY", "prepared callId=${record.callId} copyId=${copy.copyId} node=${node.name}")
        }
        if (created) {
            persistCopies(context, next)
            persistRecord(
                context,
                record.copy(
                    syncState = IdentitySyncState.MESH_COPY_ACTIVE,
                    lastMeshBroadcastAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun expireAllCopies(context: Context, callId: String) {
        val expired =
            loadCopies(context).map {
                if (it.callId == callId) {
                    it.copy(ttl = 0L, hasReachedInternet = true)
                } else {
                    it
                }
            }
        persistCopies(context, expired)
    }

    private fun canAttemptServerSync(context: Context): Boolean =
        OnlinePresenceManager.hasInternet(context) && OnlineFallbackTransport.isConfigured()

    private fun preferredRouteHint(context: Context, callId: String): String? {
        if (OnlineFallbackTransport.isConfigured()) {
            return OnlineFallbackTransport.relayBaseUrl().takeIf { it.isNotBlank() }
        }
        return IntelligentRouteMemory.getHint(context, callId)?.nextHopId
            ?: activeMeshCopies(context, callId).firstOrNull()?.routeHint
    }

    private fun registerNetworkWatcher(context: Context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request =
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
        runCatching {
            manager.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        retryPendingWhenInternetReturns(context)
                    }
                }
            )
        }
    }

    private fun persistRecord(context: Context, record: CallIdentityRecord) {
        context.prefs().edit().putString(KEY_LOCAL_RECORD, recordToJson(record).toString()).apply()
    }

    private fun loadCopies(context: Context): List<IdentityMeshCopy> {
        val raw = context.prefs().getString(KEY_MESH_COPIES, "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(copyFromJson(item))
            }
        }
    }

    private fun persistCopies(context: Context, copies: List<IdentityMeshCopy>) {
        val array = JSONArray()
        copies.forEach { copy -> array.put(copyToJson(copy)) }
        context.prefs().edit().putString(KEY_MESH_COPIES, array.toString()).apply()
    }

    private fun recordToJson(record: CallIdentityRecord): JSONObject =
        JSONObject()
            .put("callId", record.callId)
            .put("userDisplayName", record.userDisplayName)
            .put("publicKey", record.publicKey)
            .put("deviceId", record.deviceId)
            .put("createdAt", record.createdAt)
            .put("updatedAt", record.updatedAt)
            .put("syncState", record.syncState.name)
            .put("lastServerSyncAt", record.lastServerSyncAt)
            .put("lastMeshBroadcastAt", record.lastMeshBroadcastAt)
            .put("copyVersion", record.copyVersion)
            .put("signature", record.signature)

    private fun recordFromJson(item: String): CallIdentityRecord = recordFromJson(JSONObject(item))

    private fun recordFromJson(item: JSONObject): CallIdentityRecord =
        CallIdentityRecord(
            callId = item.optString("callId"),
            userDisplayName = item.optString("userDisplayName"),
            publicKey = item.optString("publicKey"),
            deviceId = item.optString("deviceId"),
            createdAt = item.optLong("createdAt"),
            updatedAt = item.optLong("updatedAt"),
            syncState = IdentitySyncState.entries.firstOrNull { it.name == item.optString("syncState") }
                ?: IdentitySyncState.LOCAL_ONLY,
            lastServerSyncAt = item.optLong("lastServerSyncAt", 0L),
            lastMeshBroadcastAt = item.optLong("lastMeshBroadcastAt", 0L),
            copyVersion = item.optInt("copyVersion", 1),
            signature = item.optString("signature").ifBlank { null }
        )

    private fun copyToJson(copy: IdentityMeshCopy): JSONObject =
        JSONObject()
            .put("callId", copy.callId)
            .put("ownerDeviceId", copy.ownerDeviceId)
            .put("copyId", copy.copyId)
            .put("copyIndex", copy.copyIndex)
            .put("maxCopies", copy.maxCopies)
            .put("hopCount", copy.hopCount)
            .put("ttl", copy.ttl)
            .put("createdAt", copy.createdAt)
            .put("lastForwardedAt", copy.lastForwardedAt)
            .put("hasReachedInternet", copy.hasReachedInternet)
            .put("routeHint", copy.routeHint)
            .put("routeScore", copy.routeScore)

    private fun copyFromJson(item: JSONObject): IdentityMeshCopy =
        IdentityMeshCopy(
            callId = item.optString("callId"),
            ownerDeviceId = item.optString("ownerDeviceId"),
            copyId = item.optString("copyId"),
            copyIndex = item.optInt("copyIndex", 1),
            maxCopies = item.optInt("maxCopies", MAX_ACTIVE_COPIES),
            hopCount = item.optInt("hopCount", 0),
            ttl = item.optLong("ttl", IdentityMeshCopy.DEFAULT_TTL_MS),
            createdAt = item.optLong("createdAt", System.currentTimeMillis()),
            lastForwardedAt = item.optLong("lastForwardedAt", System.currentTimeMillis()),
            hasReachedInternet = item.optBoolean("hasReachedInternet", false),
            routeHint = item.optString("routeHint").ifBlank { null },
            routeScore = item.optInt("routeScore", 0)
        )

    private fun nodeRouteScore(node: MeshNode): Int {
        val relayBonus = if (node.relay) 20 else 0
        val trust = node.trusted
        val signal = node.signal.coerceAtLeast(0)
        val latencyPenalty = (node.latency / 5).coerceAtLeast(0)
        return relayBonus + trust + signal - latencyPenalty
    }

    private fun hashedDeviceId(context: Context, fallback: String): String {
        val base =
            runCatching {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            }.getOrNull().orEmpty().ifBlank { fallback }
        return MessageDigest.getInstance("SHA-256")
            .digest(base.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun Context.prefs() =
        applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
