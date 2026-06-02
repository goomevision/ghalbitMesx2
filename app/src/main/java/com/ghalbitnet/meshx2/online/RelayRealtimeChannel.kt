package com.ghalbitnet.meshx2.online

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList

object RelayRealtimeChannel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listeners = CopyOnWriteArrayList<(RelayRealtimeEvent) -> Unit>()

    @Volatile
    private var connected = false

    @Volatile
    private var currentGlobalId: String? = null

    @Volatile
    private var warmRelayGlobalId: String? = null

    fun isConnected(): Boolean = connected

    fun currentBoundGlobalId(): String? = currentGlobalId

    fun addListener(listener: (RelayRealtimeEvent) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (RelayRealtimeEvent) -> Unit) {
        listeners -= listener
    }

    fun bind(context: Context, globalId: String) {
        if (globalId.isBlank() || !OnlineFallbackTransport.isConfigured()) {
            return
        }
        if (currentGlobalId == globalId) {
            return
        }
        currentGlobalId = globalId
        scope.launch {
            while (isActive && currentGlobalId == globalId) {
                val relayUrl = RelayRegistryManager.current(context)?.url ?: OnlineFallbackTransport.relayBaseUrl()
                if (relayUrl.isBlank()) {
                    connected = false
                    delay(10_000L)
                    continue
                }
                runCatching {
                    connectStream("$relayUrl/realtime/stream/$globalId")
                }.onFailure {
                    connected = false
                    Log.w("GHALBIT-REALTIME", "fallback polling ${it.message}")
                    delay(8_000L)
                }
            }
        }
    }

    fun warmRelay(context: Context, globalId: String) {
        if (globalId.isBlank() || !OnlineFallbackTransport.isConfigured()) {
            return
        }
        warmRelayGlobalId = globalId
        Log.d("GHALBIT-WARM-RELAY", "opening")
        bind(context, globalId)
        Log.d("GHALBIT-WARM-RELAY", "ready")
    }

    fun closeWarmRelay() {
        if (warmRelayGlobalId != null) {
            Log.d("GHALBIT-WARM-RELAY", "closed idle")
        }
        warmRelayGlobalId = null
    }

    fun shutdown() {
        currentGlobalId = null
        connected = false
    }

    private fun connectStream(urlValue: String) {
        val connection =
            (URL(urlValue).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 30000
                setRequestProperty("Accept", "text/event-stream")
            }
        connected = true
        Log.d("GHALBIT-REALTIME", "connected url=$urlValue")
        var currentEvent = "message"
        connection.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                when {
                    line.startsWith("event:") -> currentEvent = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> {
                        val raw = line.removePrefix("data:").trim()
                        dispatch(currentEvent, raw)
                    }
                }
            }
        }
        connected = false
        Log.d("GHALBIT-REALTIME", "disconnected url=$urlValue")
        connection.disconnect()
    }

    private fun dispatch(type: String, raw: String) {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val event =
            RelayRealtimeEvent(
                type = type,
                rawJson = raw,
                message = json.optJSONObject("message")?.let { message ->
                    RelayInboxMessage(
                        messageId = message.optString("messageId"),
                        packetId = message.optString("packetId", message.optString("messageId")),
                        senderGlobalId = message.optString("senderGlobalId"),
                        senderNodeId = message.optString("senderNodeId"),
                        senderPublicKeyHash = message.optString("senderPublicKeyHash").ifBlank { null },
                        senderPublicKey = message.optString("senderPublicKey").ifBlank { null },
                        senderDisplayName = message.optString("senderDisplayName").ifBlank { null },
                        targetGlobalId = message.optString("targetGlobalId"),
                        payload = message.optString("payload"),
                        contentType = message.optString("contentType", "TEXT"),
                        mimeType = message.optString("mimeType").ifBlank { null },
                        fileSize = message.optLong("fileSize", 0L),
                        createdAt = message.optLong("createdAt"),
                        expiresAt = message.optLong("expiresAt")
                    )
                },
                receipt = json.optJSONObject("receipt")?.let { receipt ->
                    RelayInboxReceipt(
                        receiptId = receipt.optString("receiptId"),
                        type = receipt.optString("type"),
                        messageId = receipt.optString("messageId"),
                        packetId = receipt.optString("packetId"),
                        senderGlobalId = receipt.optString("senderGlobalId"),
                        targetGlobalId = receipt.optString("targetGlobalId"),
                        createdAt = receipt.optLong("createdAt")
                    )
                },
                presence = if (type == "presence") {
                    OnlinePresence(
                        nodeId = json.optString("nodeId"),
                        globalId = json.optString("globalId"),
                        publicKeyHash = json.optString("publicKeyHash").ifBlank { null },
                        online = json.optString("status", "ONLINE_REMOTE") == "ONLINE_REMOTE",
                        route = OnlineFallbackTransport.relayBaseUrl().takeIf { it.isNotBlank() }?.let { InternetRoute(json.optString("globalId"), it) },
                        lastSeen = json.optLong("lastSeen", System.currentTimeMillis())
                    )
                } else {
                    null
                },
                edit = json.optJSONObject("edit")?.let { edit ->
                    RelayInboxEdit(
                        eventId = edit.optString("eventId"),
                        originalMessageId = edit.optString("originalMessageId"),
                        packetId = edit.optString("packetId", edit.optString("originalMessageId")),
                        senderGlobalId = edit.optString("senderGlobalId"),
                        targetGlobalId = edit.optString("targetGlobalId"),
                        content = edit.optString("content"),
                        editVersion = edit.optInt("editVersion", 1),
                        editedAt = edit.optLong("editedAt")
                    )
                },
                delete = json.optJSONObject("delete")?.let { delete ->
                    RelayInboxDelete(
                        eventId = delete.optString("eventId"),
                        originalMessageId = delete.optString("originalMessageId"),
                        packetId = delete.optString("packetId", delete.optString("originalMessageId")),
                        senderGlobalId = delete.optString("senderGlobalId"),
                        targetGlobalId = delete.optString("targetGlobalId"),
                        mode = delete.optString("mode", "DELETE_FOR_EVERYONE"),
                        deletedAt = delete.optLong("deletedAt")
                    )
                }
            )
        listeners.forEach { it(event) }
        if (event.receipt != null) {
            Log.d("GHALBIT-REALTIME", "receipt pushed ${event.receipt.messageId}")
        }
        if (warmRelayGlobalId != null) {
            Log.d("GHALBIT-WARM-RELAY", "keepalive")
        }
        if (event.edit != null) {
            Log.d("GHALBIT-REALTIME", "edit pushed id=${event.edit.originalMessageId}")
        }
        if (event.delete != null) {
            Log.d("GHALBIT-REALTIME", "delete pushed id=${event.delete.originalMessageId}")
        }
    }
}
