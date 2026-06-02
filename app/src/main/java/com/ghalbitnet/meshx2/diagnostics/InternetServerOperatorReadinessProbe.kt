package com.ghalbitnet.meshx2.diagnostics

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.BuildConfig
import com.ghalbitnet.meshx2.diagnostics.evidence.RuntimeEvidenceCollector
import com.ghalbitnet.meshx2.diagnostics.evidence.RuntimeEvidenceTags
import com.ghalbitnet.meshx2.online.InternetRoute
import com.ghalbitnet.meshx2.online.OnlineFallbackTransport
import com.ghalbitnet.meshx2.online.OnlinePresenceManager
import com.ghalbitnet.meshx2.security.NodeSigningIdentityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ServerOperatorCheckItem(
    val name: String,
    val ok: Boolean,
    val code: Int = -1,
    val latencyMs: Long = -1L,
    val detail: String = ""
)

data class ServerOperatorReadinessResult(
    val configured: Boolean,
    val baseUrl: String,
    val presenceUrl: String,
    val status: String,
    val checks: List<ServerOperatorCheckItem>
) {
    fun score(): Int {
        if (!configured) return 0
        if (checks.isEmpty()) return 0
        return ((checks.count { it.ok } * 100.0) / checks.size).toInt().coerceIn(0, 100)
    }
}

object InternetServerOperatorReadinessProbe {
    suspend fun run(context: Context): ServerOperatorReadinessResult {
        val relayBase = OnlineFallbackTransport.relayBaseUrl()
        val presenceBase = OnlineFallbackTransport.presenceBaseUrl()
        val configured = BuildConfig.INTERNET_RELAY_CONFIGURED && relayBase.isNotBlank()

        if (!configured) {
            Log.w("GHALBIT-SERVER-TRUTH", "BASE_URL relay=$relayBase presence=$presenceBase configured=false")
            Log.w("GHALBIT-SERVER-PRESENCE", "REGISTER_FAIL reason=SERVER_NOT_CONFIGURED")
            Log.w("GHALBIT-SERVER-PRESENCE", "HEARTBEAT_FAIL reason=SERVER_NOT_CONFIGURED")
            Log.w("GHALBIT-SERVER-CHAT", "SEND_FAIL reason=SERVER_NOT_CONFIGURED")
            Log.w("GHALBIT-SERVER-CALL", "START_FAIL reason=SERVER_NOT_CONFIGURED")
            RuntimeEvidenceCollector.record(
                context,
                RuntimeEvidenceTags.SERVER_NOT_CONFIGURED,
                source = "InternetServerOperatorReadinessProbe",
                status = "SERVER_NOT_CONFIGURED",
                details = "relay=$relayBase presence=$presenceBase"
            )
            return ServerOperatorReadinessResult(
                configured = false,
                baseUrl = relayBase,
                presenceUrl = presenceBase,
                status = "SERVER_NOT_CONFIGURED",
                checks = emptyList()
            )
        }

        val identity = NodeSigningIdentityManager.getOrCreate(context)
        val route = InternetRoute(identity.globalId, relayBase)
        val checks = mutableListOf<ServerOperatorCheckItem>()

        checks += checkHealth(context, relayBase)
        checks += checkRegisterHeartbeatAndPresence(context, identity.nodeId, identity.globalId, identity.publicKeyHash)
        checks += checkRelayChat(context, route, identity.nodeId, identity.globalId, identity.publicKeyHash, identity.publicKeyBase64)
        checks += checkRelayInbox(context, identity.globalId)
        checks += checkReceipts(context, identity.globalId)
        checks += checkSessionEndpoints(context, relayBase, identity.globalId)

        val proven = checks.count { it.ok }
        val status = when {
            proven == 0 -> "FAILED"
            proven == checks.size -> "READY"
            else -> "PARTIAL"
        }

        return ServerOperatorReadinessResult(
            configured = true,
            baseUrl = relayBase,
            presenceUrl = presenceBase,
            status = status,
            checks = checks
        )
    }

    private suspend fun checkHealth(context: Context, baseUrl: String): ServerOperatorCheckItem {
        Log.i("GHALBIT-SERVER-TRUTH", "PING_START")
        val health = request("GET", "${baseUrl.trimEnd('/')}/health")
        val ping = if (!health.ok) request("GET", "${baseUrl.trimEnd('/')}/ping") else health
        if (ping.ok) {
            Log.i("GHALBIT-SERVER-TRUTH", "PING_OK code=${ping.code} latencyMs=${ping.latencyMs}")
            RuntimeEvidenceCollector.record(
                context,
                RuntimeEvidenceTags.SERVER_HEALTH_OK,
                source = "InternetServerOperatorReadinessProbe",
                status = ping.code.toString(),
                details = "latencyMs=${ping.latencyMs}"
            )
        } else {
            Log.w("GHALBIT-SERVER-TRUTH", "PING_FAIL reason=${ping.error ?: "unknown"}")
            RuntimeEvidenceCollector.record(
                context,
                RuntimeEvidenceTags.SERVER_HEALTH_FAIL,
                source = "InternetServerOperatorReadinessProbe",
                status = ping.code.toString(),
                details = ping.error ?: "unknown"
            )
        }
        return ServerOperatorCheckItem("health_ping", ping.ok, ping.code, ping.latencyMs, ping.error.orEmpty())
    }

    private suspend fun checkRegisterHeartbeatAndPresence(
        context: Context,
        nodeId: String,
        globalId: String,
        publicKeyHash: String
    ): List<ServerOperatorCheckItem> {
        val registerOk = OnlinePresenceManager.registerOnline(context, nodeId, globalId, publicKeyHash)
        if (registerOk) {
            Log.i("GHALBIT-SERVER-PRESENCE", "REGISTER_OK")
            RuntimeEvidenceCollector.record(
                context,
                RuntimeEvidenceTags.SERVER_REGISTER_OK,
                source = "InternetServerOperatorReadinessProbe",
                peerId = globalId,
                status = "REGISTER_OK"
            )
        } else {
            Log.w("GHALBIT-SERVER-PRESENCE", "REGISTER_FAIL")
        }

        val heartbeat = OnlinePresenceManager.heartbeat(
            com.ghalbitnet.meshx2.online.OnlinePresence(
                nodeId = nodeId,
                globalId = globalId,
                publicKeyHash = publicKeyHash,
                online = true,
                route = InternetRoute(globalId, OnlineFallbackTransport.relayBaseUrl()),
                lastSeen = System.currentTimeMillis()
            )
        )
        if (heartbeat.online) {
            Log.i("GHALBIT-SERVER-PRESENCE", "HEARTBEAT_OK status=${heartbeat.status}")
            RuntimeEvidenceCollector.record(
                context,
                RuntimeEvidenceTags.SERVER_HEARTBEAT_OK,
                source = "InternetServerOperatorReadinessProbe",
                peerId = globalId,
                status = heartbeat.status
            )
        } else {
            Log.w("GHALBIT-SERVER-PRESENCE", "HEARTBEAT_FAIL status=${heartbeat.status} error=${heartbeat.error}")
        }

        val selfPresence = OnlinePresenceManager.checkPeerOnline(context, globalId)
        if (selfPresence != null) {
            Log.i(
                "GHALBIT-SERVER-PRESENCE",
                "ONLINE lastSeen=${selfPresence.lastSeen}"
            )
        } else {
            Log.w("GHALBIT-SERVER-PRESENCE", "OFFLINE lastSeen=-1")
        }
        return listOf(
            ServerOperatorCheckItem("register_device", registerOk, detail = if (registerOk) "ok" else "fail"),
            ServerOperatorCheckItem("heartbeat", heartbeat.online, detail = heartbeat.status),
            ServerOperatorCheckItem("lookup_self_presence", selfPresence != null, detail = "lastSeen=${selfPresence?.lastSeen ?: -1L}")
        )
    }

    private suspend fun checkRelayChat(
        context: Context,
        route: InternetRoute,
        nodeId: String,
        globalId: String,
        publicKeyHash: String,
        publicKey: String
    ): ServerOperatorCheckItem {
        val messageId = "diag-msg-${System.currentTimeMillis()}"
        val send = OnlineFallbackTransport.sendMessageViaInternet(
            context = context,
            route = route,
            packetId = messageId,
            sourceNodeId = nodeId,
            sourceGlobalId = globalId,
            sourcePublicKeyHash = publicKeyHash,
            sourcePublicKey = publicKey,
            targetNodeId = nodeId,
            targetGlobalId = globalId,
            message = "{\"kind\":\"DIAG\",\"message\":\"server-operator-check\"}",
            contentType = "DIAG"
        )
        if (send.successful) {
            Log.i("GHALBIT-SERVER-CHAT", "SEND_OK status=${send.status}")
            RuntimeEvidenceCollector.record(
                context,
                RuntimeEvidenceTags.MESSAGE_SENT_TO_SERVER,
                source = "InternetServerOperatorReadinessProbe",
                peerId = globalId,
                status = send.status
            )
            RuntimeEvidenceCollector.record(
                context,
                RuntimeEvidenceTags.SERVER_RELAY_OK,
                source = "InternetServerOperatorReadinessProbe",
                peerId = globalId,
                status = send.status
            )
        } else {
            Log.w("GHALBIT-SERVER-CHAT", "SEND_FAIL status=${send.status} error=${send.error}")
        }
        return ServerOperatorCheckItem("relay_send", send.successful, detail = send.status)
    }

    private suspend fun checkRelayInbox(context: Context, globalId: String): ServerOperatorCheckItem {
        val inbox = OnlineFallbackTransport.fetchInbox(context, globalId)
        val ok = inbox.error.isNullOrBlank()
        if (ok) Log.i("GHALBIT-SERVER-CHAT", "INBOX_OK messages=${inbox.messages.size} receipts=${inbox.receipts.size}")
        else Log.w("GHALBIT-SERVER-CHAT", "INBOX_FAIL error=${inbox.error}")
        return ServerOperatorCheckItem("relay_inbox", ok, detail = inbox.error.orEmpty())
    }

    private suspend fun checkReceipts(context: Context, globalId: String): List<ServerOperatorCheckItem> {
        val messageId = "diag-ack-${System.currentTimeMillis()}"
        val delivered = OnlineFallbackTransport.sendAck(context, globalId, globalId, messageId)
        if (delivered) Log.i("GHALBIT-SERVER-CHAT", "DELIVERED_OK")
        else Log.w("GHALBIT-SERVER-CHAT", "DELIVERED_FAIL")

        val read = OnlineFallbackTransport.sendRead(context, globalId, globalId, messageId)
        if (read) Log.i("GHALBIT-SERVER-CHAT", "READ_OK")
        else Log.w("GHALBIT-SERVER-CHAT", "READ_FAIL")

        return listOf(
            ServerOperatorCheckItem("ack_delivered", delivered, detail = if (delivered) "ok" else "fail"),
            ServerOperatorCheckItem("ack_read", read, detail = if (read) "ok" else "fail")
        )
    }

    private suspend fun checkSessionEndpoints(context: Context, baseUrl: String, globalId: String): List<ServerOperatorCheckItem> {
        val startPayload = JSONObject().put("callId", "diag-call-${System.currentTimeMillis()}").put("targetGlobalId", globalId).toString()
        val start = request("POST", "${baseUrl.trimEnd('/')}/session/start", startPayload)
        if (start.ok) {
            Log.i("GHALBIT-SERVER-CALL", "START_OK")
            RuntimeEvidenceCollector.record(
                context,
                RuntimeEvidenceTags.SERVER_SESSION_OK,
                source = "InternetServerOperatorReadinessProbe",
                peerId = globalId,
                status = "START_OK"
            )
        } else Log.w("GHALBIT-SERVER-CALL", "START_FAIL code=${start.code} error=${start.error.orEmpty()}")

        Log.i("GHALBIT-SERVER-CALL", "RINGING")
        val ringing = request("POST", "${baseUrl.trimEnd('/')}/session/ringing", startPayload)

        val accept = request("POST", "${baseUrl.trimEnd('/')}/session/accept", startPayload)
        if (accept.ok) {
            Log.i("GHALBIT-SERVER-CALL", "ACCEPT_OK")
            RuntimeEvidenceCollector.record(
                context,
                RuntimeEvidenceTags.SERVER_SESSION_OK,
                source = "InternetServerOperatorReadinessProbe",
                peerId = globalId,
                status = "ACCEPT_OK"
            )
        } else Log.w("GHALBIT-SERVER-CALL", "ACCEPT_FAIL code=${accept.code} error=${accept.error.orEmpty()}")

        val reject = request("POST", "${baseUrl.trimEnd('/')}/session/reject", startPayload)
        if (reject.ok) {
            Log.i("GHALBIT-SERVER-CALL", "REJECT_OK")
            RuntimeEvidenceCollector.record(
                context,
                RuntimeEvidenceTags.SERVER_SESSION_OK,
                source = "InternetServerOperatorReadinessProbe",
                peerId = globalId,
                status = "REJECT_OK"
            )
        } else Log.w("GHALBIT-SERVER-CALL", "REJECT_FAIL code=${reject.code} error=${reject.error.orEmpty()}")

        val end = request("POST", "${baseUrl.trimEnd('/')}/session/end", startPayload)
        if (end.ok) {
            Log.i("GHALBIT-SERVER-CALL", "END_OK")
            RuntimeEvidenceCollector.record(
                context,
                RuntimeEvidenceTags.SERVER_SESSION_OK,
                source = "InternetServerOperatorReadinessProbe",
                peerId = globalId,
                status = "END_OK"
            )
        } else Log.w("GHALBIT-SERVER-CALL", "END_FAIL code=${end.code} error=${end.error.orEmpty()}")

        return listOf(
            ServerOperatorCheckItem("session_start", start.ok, start.code, start.latencyMs, start.error.orEmpty()),
            ServerOperatorCheckItem("session_ringing", ringing.ok, ringing.code, ringing.latencyMs, ringing.error.orEmpty()),
            ServerOperatorCheckItem("session_accept", accept.ok, accept.code, accept.latencyMs, accept.error.orEmpty()),
            ServerOperatorCheckItem("session_reject", reject.ok, reject.code, reject.latencyMs, reject.error.orEmpty()),
            ServerOperatorCheckItem("session_end", end.ok, end.code, end.latencyMs, end.error.orEmpty())
        )
    }

    private data class HttpCheckResult(
        val ok: Boolean,
        val code: Int,
        val latencyMs: Long,
        val error: String? = null
    )

    private suspend fun request(method: String, url: String, body: String? = null): HttpCheckResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val started = System.currentTimeMillis()
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 3000
                    readTimeout = 3000
                    setRequestProperty("Content-Type", "application/json")
                    if (method == "POST") doOutput = true
                }
                if (method == "POST" && body != null) {
                    conn.outputStream.bufferedWriter().use { it.write(body) }
                }
                val code = conn.responseCode
                conn.inputStream?.close()
                conn.errorStream?.close()
                conn.disconnect()
                val latency = System.currentTimeMillis() - started
                HttpCheckResult(ok = code in 200..299, code = code, latencyMs = latency)
            }.getOrElse {
                HttpCheckResult(false, -1, -1L, it.message ?: "unknown")
            }
        }
}
