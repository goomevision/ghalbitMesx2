package com.ghalbitnet.meshx2.identity

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.online.OnlineFallbackTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class IdentitySyncResponse(
    val ok: Boolean,
    val conflict: Boolean = false,
    val routeHint: String? = null,
    val updatedAt: Long = 0L,
    val error: String? = null
)

data class IdentityLookupResult(
    val callId: String,
    val displayName: String,
    val publicKey: String,
    val deviceIdHash: String,
    val routeHint: String? = null,
    val relayUrl: String? = null,
    val updatedAt: Long = 0L,
    val provisional: Boolean = false
)

object IdentityServerClient {
    suspend fun registerIdentity(context: Context, record: CallIdentityRecord, routeHint: String?): IdentitySyncResponse =
        syncInternal(context, "/identity/register", record, routeHint)

    suspend fun syncIdentity(context: Context, record: CallIdentityRecord, routeHint: String?): IdentitySyncResponse =
        syncInternal(context, "/identity/sync", record, routeHint)

    suspend fun lookupIdentity(context: Context, callId: String): IdentityLookupResult? = withContext(Dispatchers.IO) {
        val baseUrl = configuredBaseUrl() ?: return@withContext null
        runCatching {
            val connection = (URL("$baseUrl/identity/lookup/$callId").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }
            val text =
                if (connection.responseCode in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    null
                }
            connection.disconnect()
            val body = text?.let(::JSONObject) ?: return@runCatching null
            if (!body.optBoolean("ok")) return@runCatching null
            val identity = body.optJSONObject("identity") ?: return@runCatching null
            IdentityLookupResult(
                callId = identity.optString("callId"),
                displayName = identity.optString("displayName"),
                publicKey = identity.optString("publicKey"),
                deviceIdHash = identity.optString("deviceIdHash"),
                routeHint = identity.optString("routeHint").ifBlank { null },
                relayUrl = identity.optString("relayUrl").ifBlank { null },
                updatedAt = identity.optLong("updatedAt", 0L),
                provisional = identity.optBoolean("provisional", false)
            )
        }.onFailure {
            Log.w("GHALBIT-IDENTITY-SERVER", "lookup failed ${it.message}")
        }.getOrNull()
    }

    suspend fun notifyCopyReachedInternet(
        context: Context,
        record: CallIdentityRecord,
        copy: IdentityMeshCopy,
        routeHint: String?
    ): IdentitySyncResponse = withContext(Dispatchers.IO) {
        val baseUrl = configuredBaseUrl() ?: return@withContext IdentitySyncResponse(false, error = "missing_config")
        val payload =
            JSONObject()
                .put("callId", record.callId)
                .put("displayName", record.userDisplayName)
                .put("publicKey", record.publicKey)
                .put("deviceIdHash", hashValue(record.deviceId))
                .put("copyId", copy.copyId)
                .put("copyIndex", copy.copyIndex)
                .put("routeHint", routeHint ?: copy.routeHint)
                .put("updatedAt", record.updatedAt)
        postJson("$baseUrl/identity/copy-reached-internet", payload)?.let(::responseFromJson)
            ?: IdentitySyncResponse(false, error = "network_error")
    }

    suspend fun sendRouteHint(context: Context, callId: String, routeHint: String): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = configuredBaseUrl() ?: return@withContext false
        val payload =
            JSONObject()
                .put("callId", callId)
                .put("routeHint", routeHint)
                .put("updatedAt", System.currentTimeMillis())
        postJson("$baseUrl/identity/route-hint", payload)?.optBoolean("ok", false) == true
    }

    private suspend fun syncInternal(
        context: Context,
        endpoint: String,
        record: CallIdentityRecord,
        routeHint: String?
    ): IdentitySyncResponse = withContext(Dispatchers.IO) {
        val baseUrl = configuredBaseUrl() ?: return@withContext IdentitySyncResponse(false, error = "missing_config")
        val payload =
            JSONObject()
                .put("callId", record.callId)
                .put("displayName", record.userDisplayName)
                .put("publicKey", record.publicKey)
                .put("deviceIdHash", hashValue(record.deviceId))
                .put("routeHint", routeHint)
                .put("updatedAt", record.updatedAt)
                .put("copyVersion", record.copyVersion)
        postJson("$baseUrl$endpoint", payload)?.let(::responseFromJson)
            ?: IdentitySyncResponse(false, error = "network_error")
    }

    private fun responseFromJson(body: JSONObject): IdentitySyncResponse =
        IdentitySyncResponse(
            ok = body.optBoolean("ok", false),
            conflict = body.optString("error") == "identity_conflict",
            routeHint = body.optString("routeHint").ifBlank { null },
            updatedAt = body.optLong("updatedAt", 0L),
            error = body.optString("error").ifBlank { null }
        )

    private fun configuredBaseUrl(): String? {
        if (!OnlineFallbackTransport.isConfigured()) return null
        return OnlineFallbackTransport.relayBaseUrl().takeIf { it.isNotBlank() }
    }

    private fun postJson(url: String, payload: JSONObject): JSONObject? {
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Content-Type", "application/json")
            }
            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(payload.toString())
            }
            val stream =
                if (connection.responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            if (text.isBlank()) null else JSONObject(text)
        }.getOrNull()
    }

    private fun hashValue(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
