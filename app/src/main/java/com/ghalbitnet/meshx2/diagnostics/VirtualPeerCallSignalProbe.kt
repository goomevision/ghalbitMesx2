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
        val result = sendSignal(
            context = appContext,
            relayBase = relayBase,
            path = "start",
            callId = callId,
            runtimeGlobalId = runtimeGlobalId,
            runtimeNodeId = runtimeNodeId
        )
        if (!result.status.endsWith("_OK")) {
            return@withContext result
        }
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_CALL_ID, callId)
            .apply()
        result
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
        val result = sendSignal(
            context = appContext,
            relayBase = relayBase,
            path = "end",
            callId = callId,
            runtimeGlobalId = runtimeGlobalId,
            runtimeNodeId = runtimeNodeId
        )
        if (result.status.endsWith("_OK")) {
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_LAST_CALL_ID)
                .apply()
        }
        result
    }

    suspend fun ringing(context: Context): VirtualPeerCallSignalProbeResult = continueSignal(context, "ringing")

    suspend fun accept(context: Context): VirtualPeerCallSignalProbeResult = continueSignal(context, "accept")

    suspend fun reject(context: Context): VirtualPeerCallSignalProbeResult = continueSignal(context, "reject")

    private suspend fun continueSignal(context: Context, path: String): VirtualPeerCallSignalProbeResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val relayBase = OnlineFallbackTransport.relayBaseUrl()
        val runtimeNodeId = MeshRuntimeManager.localNodeId().ifBlank { com.ghalbitnet.meshx2.MainActivity.myGlobalPeerId }
        val runtimeGlobalId = RelayRealtimeChannel.currentBoundGlobalId().orEmpty().ifBlank { MeshRuntimeManager.localGlobalId() }
        val callId = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_CALL_ID, "").orEmpty()
        if (!BuildConfig.INTERNET_RELAY_CONFIGURED || relayBase.isBlank() || runtimeGlobalId.isBlank() || callId.isBlank()) {
            val status = "CALL_${path.uppercase()}_MISSING_CONTEXT"
            Log.w("GHALBIT-VIRTUAL-PEER", "$status callId=$callId")
            return@withContext VirtualPeerCallSignalProbeResult(callId, status, -1, 0, 0)
        }
        sendSignal(
            context = appContext,
            relayBase = relayBase,
            path = path,
            callId = callId,
            runtimeGlobalId = runtimeGlobalId,
            runtimeNodeId = runtimeNodeId
        )
    }

    private suspend fun sendSignal(
        context: Context,
        relayBase: String,
        path: String,
        callId: String,
        runtimeGlobalId: String,
        runtimeNodeId: String
    ): VirtualPeerCallSignalProbeResult = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("callId", callId)
            .put("sourceGlobalId", VIRTUAL_SOURCE_GLOBAL_ID)
            .put("sourceNodeId", VIRTUAL_SOURCE_NODE_ID)
            .put("sourceDisplayName", VIRTUAL_SOURCE_NAME)
            .put("targetGlobalId", runtimeGlobalId)
            .put("targetNodeId", runtimeNodeId)
            .put("createdAt", System.currentTimeMillis())
        val response = postJson("${relayBase.trimEnd('/')}/session/$path", payload.toString())
        val ok = response.httpCode in 200..299
        val signalName = path.uppercase()
        Log.i(
            "GHALBIT-VIRTUAL-PEER",
            "CALL_${signalName}_${if (ok) "OK" else "FAIL"} callId=$callId target=$runtimeGlobalId code=${response.httpCode}"
        )
        RuntimeEvidenceCollector.record(
            context,
            if (ok) RuntimeEvidenceTags.SERVER_SESSION_OK else RuntimeEvidenceTags.SERVER_HEALTH_FAIL,
            source = "VirtualPeerCallSignalProbe",
            callId = callId,
            peerId = runtimeGlobalId,
            status = if (ok) "CALL_${signalName}_OK" else "CALL_${signalName}_FAIL",
            details = "code=${response.httpCode}"
        )
        val sync = ChatDeliveryManager.syncNow(context, reason = "virtual-call-signal-$path")
        VirtualPeerCallSignalProbeResult(
            callId = callId,
            status = if (ok) "CALL_${signalName}_OK" else "CALL_${signalName}_FAIL",
            httpCode = response.httpCode,
            syncMessages = sync.inboxMessages,
            syncReceipts = sync.inboxReceipts
        )
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
