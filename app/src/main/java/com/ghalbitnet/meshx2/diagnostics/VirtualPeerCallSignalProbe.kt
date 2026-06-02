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

data class VirtualPeerCallSignalProbeResult(
    val callId: String,
    val status: String,
    val httpCode: Int,
    val syncMessages: Int,
    val syncReceipts: Int
)

object VirtualPeerCallSignalProbe {
    private const val PREFS = "virtual_peer_call_signal_probe"
    private const val KEY_LAST_CALL_ID = "last_call_id"
    private const val VIRTUAL_SOURCE_GLOBAL_ID = "GX-VIRTUAL-HP-B"
    private const val VIRTUAL_SOURCE_NODE_ID = "virtual-peer-b"
    private const val VIRTUAL_SOURCE_NAME = "Virtual HP B"

    suspend fun start(context: Context): VirtualPeerCallSignalProbeResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val relayBase = OnlineFallbackTransport.relayBaseUrl()
        val runtimeNodeId = MeshRuntimeManager.localNodeId().ifBlank { com.ghalbitnet.meshx2.MainActivity.myGlobalPeerId }
        val runtimeGlobalId = RelayRealtimeChannel.currentBoundGlobalId().orEmpty().ifBlank { MeshRuntimeManager.localGlobalId() }
        if (!BuildConfig.INTERNET_RELAY_CONFIGURED || relayBase.isBlank() || runtimeGlobalId.isBlank()) {
            Log.w("GHALBIT-VIRTUAL-PEER", "CALL_START_FAIL reason=SERVER_NOT_CONFIGURED")
            return@withContext VirtualPeerCallSignalProbeResult("", "SERVER_NOT_CONFIGURED", -1, 0, 0)
        }
        val callId = "virt-server-${System.currentTimeMillis()}"
        val payload = JSONObject()
            .put("callId", callId)
            .put("sourceGlobalId", VIRTUAL_SOURCE_GLOBAL_ID)
            .put("sourceNodeId", VIRTUAL_SOURCE_NODE_ID)
            .put("sourceDisplayName", VIRTUAL_SOURCE_NAME)
            .put("targetGlobalId", runtimeGlobalId)
            .put("targetNodeId", runtimeNodeId)
            .put("createdAt", System.currentTimeMillis())
        val response = postJson("${relayBase.trimEnd('/')}/session/start", payload.toString())
        val ok = response.httpCode in 200..299
        Log.i(
            "GHALBIT-VIRTUAL-PEER",
            "CALL_START_${if (ok) "OK" else "FAIL"} callId=$callId target=$runtimeGlobalId code=${response.httpCode}"
        )
        RuntimeEvidenceCollector.record(
            appContext,
            if (ok) RuntimeEvidenceTags.SERVER_SESSION_OK else RuntimeEvidenceTags.SERVER_HEALTH_FAIL,
            source = "VirtualPeerCallSignalProbe",
            callId = callId,
            peerId = runtimeGlobalId,
            status = if (ok) "CALL_START_OK" else "CALL_START_FAIL",
            details = "code=${response.httpCode}"
        )
        if (!ok) {
            return@withContext VirtualPeerCallSignalProbeResult(callId, "CALL_START_FAIL", response.httpCode, 0, 0)
        }
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_CALL_ID, callId)
            .apply()
        val sync = ChatDeliveryManager.syncNow(appContext, reason = "virtual-call-signal-start")
        VirtualPeerCallSignalProbeResult(callId, "CALL_START_OK", response.httpCode, sync.inboxMessages, sync.inboxReceipts)
    }

    suspend fun end(context: Context): VirtualPeerCallSignalProbeResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val relayBase = OnlineFallbackTransport.relayBaseUrl()
        val runtimeNodeId = MeshRuntimeManager.localNodeId().ifBlank { com.ghalbitnet.meshx2.MainActivity.myGlobalPeerId }
        val runtimeGlobalId = RelayRealtimeChannel.currentBoundGlobalId().orEmpty().ifBlank { MeshRuntimeManager.localGlobalId() }
        val callId = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_CALL_ID, "").orEmpty()
        if (!BuildConfig.INTERNET_RELAY_CONFIGURED || relayBase.isBlank() || runtimeGlobalId.isBlank() || callId.isBlank()) {
            Log.w("GHALBIT-VIRTUAL-PEER", "CALL_END_FAIL reason=MISSING_CONTEXT callId=$callId")
            return@withContext VirtualPeerCallSignalProbeResult(callId, "CALL_END_MISSING_CONTEXT", -1, 0, 0)
        }
        val payload = JSONObject()
            .put("callId", callId)
            .put("sourceGlobalId", VIRTUAL_SOURCE_GLOBAL_ID)
            .put("sourceNodeId", VIRTUAL_SOURCE_NODE_ID)
            .put("sourceDisplayName", VIRTUAL_SOURCE_NAME)
            .put("targetGlobalId", runtimeGlobalId)
            .put("targetNodeId", runtimeNodeId)
            .put("createdAt", System.currentTimeMillis())
        val response = postJson("${relayBase.trimEnd('/')}/session/end", payload.toString())
        val ok = response.httpCode in 200..299
        Log.i(
            "GHALBIT-VIRTUAL-PEER",
            "CALL_END_${if (ok) "OK" else "FAIL"} callId=$callId target=$runtimeGlobalId code=${response.httpCode}"
        )
        RuntimeEvidenceCollector.record(
            appContext,
            if (ok) RuntimeEvidenceTags.CALL_ENDED else RuntimeEvidenceTags.SERVER_HEALTH_FAIL,
            source = "VirtualPeerCallSignalProbe",
            callId = callId,
            peerId = runtimeGlobalId,
            status = if (ok) "CALL_END_OK" else "CALL_END_FAIL",
            details = "code=${response.httpCode}"
        )
        if (ok) {
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_LAST_CALL_ID)
                .apply()
        }
        val sync = ChatDeliveryManager.syncNow(appContext, reason = "virtual-call-signal-end")
        VirtualPeerCallSignalProbeResult(callId, if (ok) "CALL_END_OK" else "CALL_END_FAIL", response.httpCode, sync.inboxMessages, sync.inboxReceipts)
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
