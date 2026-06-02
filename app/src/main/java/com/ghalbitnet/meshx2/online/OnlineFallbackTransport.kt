package com.ghalbitnet.meshx2.online

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.BuildConfig
import com.ghalbitnet.meshx2.security.NodeSigningIdentityManager
import com.ghalbitnet.meshx2.util.LogThrottle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class PreparedRouteResponse(
    val relaySessionId: String? = null,
    val relayUrl: String? = null,
    val routeToken: String? = null,
    val expiresAt: Long = 0L,
    val recommendedMode: String = "AUTO_HYBRID",
    val ready: Boolean = false,
    val healthScore: Int = 0
)

object OnlineFallbackTransport : MessageRelayApi {
    fun isConfigured(): Boolean = BuildConfig.INTERNET_RELAY_CONFIGURED

    fun relayBaseUrl(): String = BuildConfig.BASE_RELAY_URL.trim().trimEnd('/')

    fun presenceBaseUrl(): String = BuildConfig.BASE_PRESENCE_URL.trim().ifBlank { BuildConfig.BASE_RELAY_URL.trim() }.trimEnd('/')

    suspend fun sendMessageViaInternet(
        context: Context,
        route: InternetRoute,
        packetId: String,
        sourceNodeId: String,
        sourceGlobalId: String?,
        sourcePublicKeyHash: String?,
        sourcePublicKey: String?,
        targetNodeId: String,
        targetGlobalId: String?,
        message: String,
        contentType: String = "TEXT",
        senderDisplayName: String? = null
    ): RelaySendResult {
        if (!isConfigured()) {
            LogThrottle.w("GHALBIT-ANDROID-RELAY", "relay-missing:sendMessage", "missing config", 10_000L, context)
            return RelaySendResult(false, "INTERNET_RELAY_NOT_CONFIGURED", packetId, error = "missing_config")
        }
        val createdAt = System.currentTimeMillis()
        val targetGlobal = targetGlobalId.orEmpty()
        val signingIdentity = NodeSigningIdentityManager.getOrCreate(context)
        val proof =
            RelaySecurityProof.Payload(
                senderGlobalId = signingIdentity.globalId,
                targetGlobalId = targetGlobal,
                messageId = packetId,
                packetId = packetId,
                createdAt = createdAt,
                expiresAt = createdAt + 24 * 60 * 60 * 1000L,
                nonce = RelaySecurityProof.nonce(),
                contentType = contentType,
                payload = message,
                senderPublicKey = signingIdentity.publicKeyBase64
            )
        val payload =
            JSONObject()
                .put("messageId", packetId)
                .put("packetId", packetId)
                .put("type", "CHAT")
                .put("contentType", contentType)
                .put("senderNodeId", signingIdentity.nodeId)
                .put("senderGlobalId", signingIdentity.globalId)
                .put("publicKeyHash", signingIdentity.publicKeyHash)
                .put("senderPublicKey", signingIdentity.publicKeyBase64)
                .put("algorithm", proof.algorithm)
                .put("senderDisplayName", senderDisplayName)
                .put("targetNodeId", targetNodeId)
                .put("targetGlobalId", targetGlobalId)
                .put("payload", message)
                .put("createdAt", proof.createdAt)
                .put("expiresAt", proof.expiresAt)
                .put("nonce", proof.nonce)
                .put("signature", NodeSigningIdentityManager.sign(context, RelaySecurityProof.canonical(proof), proof.messageId))
        Log.d("GHALBIT-ANDROID-RELAY", "send messageId=$packetId")
        val candidates = RelayRegistryManager.all(context).map { it.url } + route.relayUrl
        return postJsonCandidates(candidates.distinct(), payload.toString(), "GHALBIT-INTERNET-TX")
    }

    suspend fun sendCallSignalViaInternet(
        context: Context,
        route: InternetRoute,
        type: String,
        payload: String
    ): Boolean {
        val validation = RelayConfigValidator.validate(context)
        when (validation.state) {
            RelayConfigValidation.State.INTERNET_RELAY_NOT_CONFIGURED -> {
                Log.w("GHALBIT-ROUTE-MODE", "relay blocked reason=missingConfig")
                return false
            }
            RelayConfigValidation.State.INTERNET_RELAY_UNREACHABLE -> {
                Log.w("GHALBIT-RELAY-CONFIG", "unreachable")
                return false
            }
            RelayConfigValidation.State.INTERNET_RELAY_READY -> Unit
        }
        val result = sendSignal(route, JSONObject().put("type", type).put("payload", payload).toString())
        Log.d("GHALBIT-ONLINE", "call signal fallback type=$type route=${route.relayUrl} ok=${result.successful}")
        return result.successful
    }

    suspend fun sendSosViaInternet(
        context: Context,
        route: InternetRoute,
        sourceNodeId: String,
        sourceGlobalId: String?,
        sourcePublicKeyHash: String?,
        sourcePublicKey: String?,
        message: String
    ): Boolean {
        val createdAt = System.currentTimeMillis()
        val signingIdentity = NodeSigningIdentityManager.getOrCreate(context)
        val proof =
            RelaySecurityProof.Payload(
                senderGlobalId = signingIdentity.globalId,
                targetGlobalId = "SOS",
                messageId = "SOS-$createdAt",
                packetId = "SOS-$createdAt",
                createdAt = createdAt,
                expiresAt = createdAt + 60 * 60 * 1000L,
                nonce = RelaySecurityProof.nonce(),
                contentType = "SOS",
                payload = message,
                senderPublicKey = signingIdentity.publicKeyBase64
            )
        val payload =
            JSONObject()
                .put("messageId", proof.messageId)
                .put("packetId", proof.packetId)
                .put("contentType", "SOS")
                .put("senderNodeId", signingIdentity.nodeId)
                .put("senderGlobalId", signingIdentity.globalId)
                .put("publicKeyHash", signingIdentity.publicKeyHash)
                .put("senderPublicKey", signingIdentity.publicKeyBase64)
                .put("algorithm", proof.algorithm)
                .put("targetNodeId", "SOS")
                .put("targetGlobalId", "SOS")
                .put("payload", message)
                .put("createdAt", proof.createdAt)
                .put("expiresAt", proof.expiresAt)
                .put("nonce", proof.nonce)
                .put("signature", NodeSigningIdentityManager.sign(context, RelaySecurityProof.canonical(proof), proof.messageId))
        val candidates = RelayRegistryManager.all(context).map { it.url } + route.relayUrl
        return postJsonCandidates(candidates.distinct(), payload.toString(), "GHALBIT-INTERNET-TX").successful
    }

    suspend fun sendControlViaInternet(
        route: InternetRoute,
        type: String,
        payload: String
    ): Boolean {
        return sendSignal(route, JSONObject().put("type", type).put("payload", payload).toString()).successful
    }

    suspend fun prepareRouteSession(
        context: Context,
        sessionId: String,
        peerGlobalId: String,
        primaryRoute: String
    ): PreparedRouteResponse {
        Log.d("GHALBIT-ROUTE-COORD", "request prepare")
        if (!isConfigured()) {
            return PreparedRouteResponse(recommendedMode = "MESH_ONLY", ready = false)
        }
        val payload =
            JSONObject()
                .put("sessionId", sessionId)
                .put("peerGlobalId", peerGlobalId)
                .put("primaryRoute", primaryRoute)
                .put("senderGlobalId", com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager.localGlobalId())
        return postJson("${relayBaseUrl()}/session/prepare-route", payload.toString(), "GHALBIT-ROUTE-COORD")
            .asPreparedRouteResponse()
    }

    suspend fun validatePreparedRoute(
        context: Context,
        candidate: PreparedRouteCandidate
    ): PreparedRouteResponse {
        if (!isConfigured()) {
            return PreparedRouteResponse(ready = false)
        }
        val payload =
            JSONObject()
                .put("sessionId", candidate.sessionId)
                .put("peerGlobalId", candidate.peerGlobalId)
                .put("relaySessionId", candidate.relaySessionId)
                .put("relayUrl", candidate.relayUrl)
                .put("routeToken", candidate.routeToken)
                .put("senderGlobalId", com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager.localGlobalId())
        return postJson("${relayBaseUrl()}/session/validate-route", payload.toString(), "GHALBIT-ROUTE-COORD")
            .asPreparedRouteResponse()
    }

    suspend fun heartbeatPreparedRoute(
        context: Context,
        candidate: PreparedRouteCandidate
    ): PreparedRouteResponse {
        if (!isConfigured()) {
            return PreparedRouteResponse(ready = false)
        }
        val payload =
            JSONObject()
                .put("sessionId", candidate.sessionId)
                .put("peerGlobalId", candidate.peerGlobalId)
                .put("relaySessionId", candidate.relaySessionId)
                .put("senderGlobalId", com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager.localGlobalId())
        return postJson("${relayBaseUrl()}/session/heartbeat", payload.toString(), "GHALBIT-ROUTE-COORD")
            .asPreparedRouteResponse()
    }

    override suspend fun sendMessage(route: InternetRoute, payload: String): RelaySendResult {
        return postJsonCandidates(listOf(route.relayUrl.ifBlank { relayBaseUrl() }.trimEnd('/')), payload, "GHALBIT-INTERNET-TX")
    }

    override suspend fun sendSignal(route: InternetRoute, payload: String): RelaySendResult {
        return postJsonCandidates(listOf(route.relayUrl.ifBlank { relayBaseUrl() }.trimEnd('/')), payload, "GHALBIT-INTERNET-TX")
    }

    override suspend fun sendSos(route: InternetRoute, payload: String): RelaySendResult {
        return postJsonCandidates(listOf(route.relayUrl.ifBlank { relayBaseUrl() }.trimEnd('/')), payload, "GHALBIT-INTERNET-TX")
    }

    suspend fun fetchInbox(context: Context, globalId: String): RelayInboxResult {
        if (!isConfigured()) {
            LogThrottle.w("GHALBIT-ANDROID-RELAY", "relay-missing:fetchInbox", "missing config", 10_000L, context)
            return RelayInboxResult(emptyList(), emptyList(), emptyList(), emptyList(), "missing_config")
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val relayUrl = RelayRegistryManager.current(context)?.url ?: relayBaseUrl()
                val startedAt = System.currentTimeMillis()
                val connection = (URL("${relayUrl.trimEnd('/')}/relay/inbox/$globalId").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                RelayRegistryManager.markSuccess(relayUrl, System.currentTimeMillis() - startedAt)
                val body = JSONObject(text)
                val messagesArray = body.optJSONArray("messages") ?: JSONArray()
                val receiptsArray = body.optJSONArray("receipts") ?: JSONArray()
                val messages =
                    buildList {
                        for (i in 0 until messagesArray.length()) {
                            val item = messagesArray.getJSONObject(i)
                            add(
                                RelayInboxMessage(
                                    messageId = item.optString("messageId"),
                                    packetId = item.optString("packetId", item.optString("messageId")),
                                    senderGlobalId = item.optString("senderGlobalId"),
                                    senderNodeId = item.optString("senderNodeId"),
                                    senderPublicKeyHash = item.optString("senderPublicKeyHash").ifBlank { null },
                                    senderPublicKey = item.optString("senderPublicKey").ifBlank { null },
                                    senderDisplayName = item.optString("senderDisplayName").ifBlank { null },
                                    targetGlobalId = item.optString("targetGlobalId"),
                                    payload = item.optString("payload"),
                                    contentType = item.optString("contentType", "TEXT"),
                                    mimeType = item.optString("mimeType").ifBlank { null },
                                    fileSize = item.optLong("fileSize", 0L),
                                    createdAt = item.optLong("createdAt"),
                                    expiresAt = item.optLong("expiresAt")
                                )
                            )
                        }
                    }
                val receipts =
                    buildList {
                        for (i in 0 until receiptsArray.length()) {
                            val item = receiptsArray.getJSONObject(i)
                            add(
                                RelayInboxReceipt(
                                    receiptId = item.optString("receiptId"),
                                    type = item.optString("type"),
                                    messageId = item.optString("messageId"),
                                    packetId = item.optString("packetId"),
                                    senderGlobalId = item.optString("senderGlobalId"),
                                    targetGlobalId = item.optString("targetGlobalId"),
                                    createdAt = item.optLong("createdAt")
                                )
                            )
                        }
                    }
                Log.d("GHALBIT-ANDROID-RELAY", "pull inbox count=${messages.size + receipts.size}")
                RelayInboxResult(messages, receipts)
            }.getOrElse {
                RelayRegistryManager.current(context)?.url?.let { RelayRegistryManager.markFailure(it) }
                Log.e("GHALBIT-INTERNET-RX", "pull inbox failed", it)
                RelayInboxResult(emptyList(), emptyList(), emptyList(), emptyList(), it.message)
            }
        }
    }

    suspend fun fetchEdits(context: Context, globalId: String): List<RelayInboxEdit> =
        fetchEventList(context, "${relayBaseUrl()}/relay/edits/$globalId") { item ->
            RelayInboxEdit(
                eventId = item.optString("eventId"),
                originalMessageId = item.optString("originalMessageId"),
                packetId = item.optString("packetId", item.optString("originalMessageId")),
                senderGlobalId = item.optString("senderGlobalId"),
                targetGlobalId = item.optString("targetGlobalId"),
                content = item.optString("content"),
                editVersion = item.optInt("editVersion", 1),
                editedAt = item.optLong("editedAt")
            )
        }

    suspend fun fetchDeletes(context: Context, globalId: String): List<RelayInboxDelete> =
        fetchEventList(context, "${relayBaseUrl()}/relay/deletes/$globalId") { item ->
            RelayInboxDelete(
                eventId = item.optString("eventId"),
                originalMessageId = item.optString("originalMessageId"),
                packetId = item.optString("packetId", item.optString("originalMessageId")),
                senderGlobalId = item.optString("senderGlobalId"),
                targetGlobalId = item.optString("targetGlobalId"),
                mode = item.optString("mode", "DELETE_FOR_EVERYONE"),
                deletedAt = item.optLong("deletedAt")
            )
        }

    suspend fun sendAck(context: Context, globalId: String, targetGlobalId: String, messageId: String): Boolean {
        val now = System.currentTimeMillis()
        val signingIdentity = NodeSigningIdentityManager.getOrCreate(context)
        val senderGlobalId = globalId.ifBlank { com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager.localGlobalId().ifBlank { signingIdentity.globalId } }
        val senderNodeId = com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager.localNodeId().ifBlank { signingIdentity.nodeId }
        val proof =
            RelaySecurityProof.Payload(
                senderGlobalId = senderGlobalId,
                targetGlobalId = targetGlobalId,
                messageId = messageId,
                packetId = messageId,
                createdAt = now,
                expiresAt = now + 60 * 60 * 1000L,
                nonce = RelaySecurityProof.nonce(),
                contentType = "ACK",
                payload = messageId,
                senderPublicKey = signingIdentity.publicKeyBase64
            )
        val payload =
            JSONObject()
                .put("senderGlobalId", senderGlobalId)
                .put("sourceGlobalId", senderGlobalId)
                .put("globalId", senderGlobalId)
                .put("senderNodeId", senderNodeId)
                .put("targetGlobalId", targetGlobalId)
                .put("messageId", messageId)
                .put("packetId", messageId)
                .put("createdAt", now)
                .put("expiresAt", proof.expiresAt)
                .put("nonce", proof.nonce)
                .put("contentType", "ACK")
                .put("payload", messageId)
                .put("senderPublicKey", signingIdentity.publicKeyBase64)
                .put("publicKeyHash", signingIdentity.publicKeyHash)
                .put("algorithm", proof.algorithm)
                .put("signature", NodeSigningIdentityManager.sign(context, RelaySecurityProof.canonical(proof), proof.messageId))
        val ok = postJson("${relayBaseUrl()}/receipt/delivered", payload.toString(), "GHALBIT-INTERNET-TX").successful
        if (ok) Log.d("GHALBIT-ANDROID-RELAY", "ack sent messageId=$messageId")
        return ok
    }

    suspend fun sendRead(context: Context, globalId: String, targetGlobalId: String, messageId: String): Boolean {
        val now = System.currentTimeMillis()
        val signingIdentity = NodeSigningIdentityManager.getOrCreate(context)
        val senderGlobalId = globalId.ifBlank { com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager.localGlobalId().ifBlank { signingIdentity.globalId } }
        val senderNodeId = com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager.localNodeId().ifBlank { signingIdentity.nodeId }
        val proof =
            RelaySecurityProof.Payload(
                senderGlobalId = senderGlobalId,
                targetGlobalId = targetGlobalId,
                messageId = messageId,
                packetId = messageId,
                createdAt = now,
                expiresAt = now + 60 * 60 * 1000L,
                nonce = RelaySecurityProof.nonce(),
                contentType = "READ",
                payload = messageId,
                senderPublicKey = signingIdentity.publicKeyBase64
            )
        val payload =
            JSONObject()
                .put("senderGlobalId", senderGlobalId)
                .put("sourceGlobalId", senderGlobalId)
                .put("globalId", senderGlobalId)
                .put("senderNodeId", senderNodeId)
                .put("targetGlobalId", targetGlobalId)
                .put("messageId", messageId)
                .put("packetId", messageId)
                .put("createdAt", now)
                .put("expiresAt", proof.expiresAt)
                .put("nonce", proof.nonce)
                .put("contentType", "READ")
                .put("payload", messageId)
                .put("senderPublicKey", signingIdentity.publicKeyBase64)
                .put("publicKeyHash", signingIdentity.publicKeyHash)
                .put("algorithm", proof.algorithm)
                .put("signature", NodeSigningIdentityManager.sign(context, RelaySecurityProof.canonical(proof), proof.messageId))
        val ok = postJson("${relayBaseUrl()}/receipt/read", payload.toString(), "GHALBIT-INTERNET-TX").successful
        if (ok) Log.d("GHALBIT-ANDROID-RELAY", "read sent messageId=$messageId")
        return ok
    }

    suspend fun sendEdit(
        context: Context,
        targetGlobalId: String,
        packetId: String,
        content: String,
        editVersion: Int
    ): Boolean {
        val now = System.currentTimeMillis()
        val signingIdentity = NodeSigningIdentityManager.getOrCreate(context)
        val proof =
            RelaySecurityProof.Payload(
                senderGlobalId = signingIdentity.globalId,
                targetGlobalId = targetGlobalId,
                messageId = packetId,
                packetId = packetId,
                createdAt = now,
                expiresAt = now + 15 * 60 * 1000L,
                nonce = RelaySecurityProof.nonce(),
                contentType = "MESSAGE_EDIT",
                payload = content,
                senderPublicKey = signingIdentity.publicKeyBase64
            )
        val payload =
            JSONObject()
                .put("eventId", "EDIT-$packetId-$now")
                .put("senderGlobalId", signingIdentity.globalId)
                .put("senderNodeId", signingIdentity.nodeId)
                .put("targetGlobalId", targetGlobalId)
                .put("originalMessageId", packetId)
                .put("packetId", packetId)
                .put("content", content)
                .put("editVersion", editVersion)
                .put("editedAt", now)
                .put("createdAt", now)
                .put("expiresAt", proof.expiresAt)
                .put("nonce", proof.nonce)
                .put("contentType", "MESSAGE_EDIT")
                .put("payload", content)
                .put("senderPublicKey", signingIdentity.publicKeyBase64)
                .put("publicKeyHash", signingIdentity.publicKeyHash)
                .put("algorithm", proof.algorithm)
                .put("signature", NodeSigningIdentityManager.sign(context, RelaySecurityProof.canonical(proof), proof.messageId))
        return postJson("${relayBaseUrl()}/relay/message/edit", payload.toString(), "GHALBIT-INTERNET-TX").successful
    }

    suspend fun sendDelete(
        context: Context,
        targetGlobalId: String,
        packetId: String,
        mode: String
    ): Boolean {
        val now = System.currentTimeMillis()
        val signingIdentity = NodeSigningIdentityManager.getOrCreate(context)
        val proof =
            RelaySecurityProof.Payload(
                senderGlobalId = signingIdentity.globalId,
                targetGlobalId = targetGlobalId,
                messageId = packetId,
                packetId = packetId,
                createdAt = now,
                expiresAt = now + 60 * 60 * 1000L,
                nonce = RelaySecurityProof.nonce(),
                contentType = "MESSAGE_DELETE",
                payload = mode,
                senderPublicKey = signingIdentity.publicKeyBase64
            )
        val payload =
            JSONObject()
                .put("eventId", "DELETE-$packetId-$now")
                .put("senderGlobalId", signingIdentity.globalId)
                .put("senderNodeId", signingIdentity.nodeId)
                .put("targetGlobalId", targetGlobalId)
                .put("originalMessageId", packetId)
                .put("packetId", packetId)
                .put("mode", mode)
                .put("deletedAt", now)
                .put("createdAt", now)
                .put("expiresAt", proof.expiresAt)
                .put("nonce", proof.nonce)
                .put("contentType", "MESSAGE_DELETE")
                .put("payload", mode)
                .put("senderPublicKey", signingIdentity.publicKeyBase64)
                .put("publicKeyHash", signingIdentity.publicKeyHash)
                .put("algorithm", proof.algorithm)
                .put("signature", NodeSigningIdentityManager.sign(context, RelaySecurityProof.canonical(proof), proof.messageId))
        return postJson("${relayBaseUrl()}/relay/message/delete", payload.toString(), "GHALBIT-INTERNET-TX").successful
    }

    fun receiveInternetPacket(rawPayload: String) {
        Log.d("GHALBIT-INTERNET-RX", rawPayload.take(240))
    }

    private suspend fun postJsonCandidates(urls: List<String>, payload: String, tag: String): RelaySendResult =
        withContext(Dispatchers.IO) {
            val candidates = urls.filter { it.isNotBlank() }.ifEmpty { listOf(relayBaseUrl()).filter { it.isNotBlank() } }
            var lastFailure = RelaySendResult(false, "FAILED", "", error = "no_candidate")
            candidates.forEach { baseUrl ->
                val startedAt = System.currentTimeMillis()
                val result = postJson("$baseUrl/relay/send", payload, tag)
                if (result.successful) {
                    RelayRegistryManager.markSuccess(baseUrl, System.currentTimeMillis() - startedAt)
                    Log.d("GHALBIT-NETWORK", "relay latency=${System.currentTimeMillis() - startedAt} url=$baseUrl")
                    return@withContext result
                }
                RelayRegistryManager.markFailure(baseUrl)
                lastFailure = result
            }
            Log.w("GHALBIT-NETWORK", "route switched relay failover")
            lastFailure
        }

    private suspend fun postJson(urlValue: String, payload: String, tag: String): RelaySendResult =
        withContext(Dispatchers.IO) {
            if (urlValue.isBlank()) {
                Log.w("GHALBIT-ANDROID-RELAY", "missing config")
                return@withContext RelaySendResult(false, "INTERNET_RELAY_NOT_CONFIGURED", "", error = "missing_config")
            }

            runCatching {
                val connection = (URL(urlValue).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 4000
                    readTimeout = 4000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                connection.outputStream.bufferedWriter().use { it.write(payload) }
                val responseBody =
                    (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()
                val json = if (responseBody.isBlank()) {
                    JSONObject()
                } else {
                    runCatching { JSONObject(responseBody) }
                        .getOrElse {
                            JSONObject()
                                .put("ok", connection.responseCode in 200..299)
                                .put("status", if (connection.responseCode in 200..299) "ACCEPTED" else "FAILED")
                                .put("error", "non_json_response")
                        }
                }
                val ok = connection.responseCode in 200..299 && json.optBoolean("ok", true)
                val status = json.optString("status", if (ok) "ACCEPTED" else "FAILED")
                val result =
                    RelaySendResult(
                        successful = ok,
                        status = status,
                        messageId = json.optString("messageId"),
                        expiresAt = json.optLong("expiresAt", 0L),
                        error = json.optString("error").ifBlank { null },
                        responseBody = responseBody
                    )
                Log.d(tag, "url=$urlValue ok=$ok status=$status code=${connection.responseCode}")
                connection.disconnect()
                result
            }.getOrElse {
                Log.e(tag, "failed url=$urlValue", it)
                RelaySendResult(false, "FAILED", "", error = it.message)
            }
        }

    private suspend fun <T> fetchEventList(
        context: Context,
        urlValue: String,
        mapper: (JSONObject) -> T
    ): List<T> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            emptyList()
        } else {
            runCatching {
                val relayUrl = RelayRegistryManager.current(context)?.url ?: relayBaseUrl()
                val connection = (URL(urlValue.replace(relayBaseUrl(), relayUrl)).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val body = JSONObject(text)
                val items = body.optJSONArray("items") ?: JSONArray()
                buildList {
                    for (i in 0 until items.length()) {
                        add(mapper(items.getJSONObject(i)))
                    }
                }
            }.getOrElse { emptyList() }
        }
    }
}

private fun RelaySendResult.asPreparedRouteResponse(): PreparedRouteResponse {
    val parsed = runCatching { JSONObject(responseBody ?: "") }.getOrNull()
    val relaySessionId = parsed?.optString("relaySessionId")?.ifBlank { null }
    val relayUrl = parsed?.optString("relayUrl")?.ifBlank { null }
    val routeToken = parsed?.optString("routeToken")?.ifBlank { null }
    return PreparedRouteResponse(
        relaySessionId = relaySessionId,
        relayUrl = relayUrl,
        routeToken = routeToken,
        expiresAt = parsed?.optLong("expiresAt", 0L) ?: 0L,
        recommendedMode = parsed?.optString("recommendedMode", "AUTO_HYBRID") ?: "AUTO_HYBRID",
        ready = successful || parsed?.optBoolean("ready") == true,
        healthScore = parsed?.optInt("healthScore", if (successful) 70 else 0) ?: if (successful) 70 else 0
    )
}
