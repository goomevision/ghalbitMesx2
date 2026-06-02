package com.ghalbitnet.meshx2.diagnostics

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.BuildConfig
import com.ghalbitnet.meshx2.chat.ChatDeliveryManager
import com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager
import com.ghalbitnet.meshx2.diagnostics.evidence.RuntimeEvidenceCollector
import com.ghalbitnet.meshx2.diagnostics.evidence.RuntimeEvidenceTags
import com.ghalbitnet.meshx2.online.OnlineFallbackTransport
import com.ghalbitnet.meshx2.online.RelayRealtimeChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class VirtualPeerChatProbeResult(
    val packetId: String,
    val status: String,
    val httpCode: Int,
    val syncMessages: Int,
    val syncReceipts: Int,
    val message: String
)

object VirtualPeerChatProbe {
    private const val VIRTUAL_SOURCE_GLOBAL_ID = "GX-VIRTUAL-HP-B"
    private const val VIRTUAL_SOURCE_NODE_ID = "virtual-peer-b"
    private const val VIRTUAL_SOURCE_NAME = "Virtual HP B"

    suspend fun send(
        context: Context,
        message: String = "Halo dari Virtual HP B"
    ): VirtualPeerChatProbeResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val relayBase = OnlineFallbackTransport.relayBaseUrl()
        val runtimeNodeId = MeshRuntimeManager.localNodeId().ifBlank { com.ghalbitnet.meshx2.MainActivity.myGlobalPeerId }
        val runtimeGlobalId = RelayRealtimeChannel.currentBoundGlobalId().orEmpty().ifBlank { MeshRuntimeManager.localGlobalId() }
        if (!BuildConfig.INTERNET_RELAY_CONFIGURED || relayBase.isBlank() || runtimeGlobalId.isBlank()) {
            Log.w("GHALBIT-VIRTUAL-PEER", "CHAT_SEND_FAIL reason=SERVER_NOT_CONFIGURED")
            return@withContext VirtualPeerChatProbeResult("", "SERVER_NOT_CONFIGURED", -1, 0, 0, message)
        }
        val packetId = "CHAT-VIRTUAL-B-${System.currentTimeMillis()}"
        val payload = JSONObject()
            .put("messageId", packetId)
            .put("eventId", packetId)
            .put("contentType", "TEXT")
            .put("type", "TEXT")
            .put("payload", message)
            .put("sourceGlobalId", VIRTUAL_SOURCE_GLOBAL_ID)
            .put("senderGlobalId", VIRTUAL_SOURCE_GLOBAL_ID)
            .put("sourceNodeId", VIRTUAL_SOURCE_NODE_ID)
            .put("senderNodeId", VIRTUAL_SOURCE_NODE_ID)
            .put("sourceDisplayName", VIRTUAL_SOURCE_NAME)
            .put("senderDisplayName", VIRTUAL_SOURCE_NAME)
            .put("targetGlobalId", runtimeGlobalId)
            .put("targetNodeId", runtimeNodeId)
            .put("createdAt", System.currentTimeMillis())
        val response = postJson("${relayBase.trimEnd('/')}/relay/send", payload.toString())
        val ok = response.httpCode in 200..299
        Log.i(
            "GHALBIT-VIRTUAL-PEER",
            "CHAT_SEND_${if (ok) "OK" else "FAIL"} packetId=$packetId target=$runtimeGlobalId code=${response.httpCode}"
        )
        RuntimeEvidenceCollector.record(
            appContext,
            if (ok) RuntimeEvidenceTags.MESSAGE_CREATED else RuntimeEvidenceTags.SERVER_HEALTH_FAIL,
            source = "VirtualPeerChatProbe",
            messageId = packetId,
            peerId = runtimeGlobalId,
            status = if (ok) "CHAT_SEND_OK" else "CHAT_SEND_FAIL",
            details = "code=${response.httpCode}"
        )
        if (!ok) {
            return@withContext VirtualPeerChatProbeResult(packetId, "CHAT_SEND_FAIL", response.httpCode, 0, 0, message)
        }
        val sync = ChatDeliveryManager.syncNow(appContext, reason = "virtual-chat-send")
        VirtualPeerChatProbeResult(packetId, "CHAT_SEND_OK", response.httpCode, sync.inboxMessages, sync.inboxReceipts, message)
    }

    private fun postJson(urlValue: String, body: String): HttpResult {
        val connection = (URL(urlValue).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 5000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        return runCatching {
            connection.outputStream.bufferedWriter().use { it.write(body) }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            HttpResult(code, text)
        }.getOrElse {
            HttpResult(-1, it.message.orEmpty())
        }.also {
            connection.disconnect()
        }
    }

    private data class HttpResult(val httpCode: Int, val body: String)
}
