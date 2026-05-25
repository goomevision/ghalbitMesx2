package com.ghalbitnet.meshx2.core.server

import android.content.Context
import com.ghalbitnet.meshx2.model.MeshNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object MeshServerApiClient {

    private const val PREFS_NAME = "mesh_server_runtime"
    private const val KEY_LAST_ATTEMPT = "last_attempt"
    private const val KEY_LAST_SUCCESS = "last_success"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_LAST_REMOTE_ONLINE_COUNT = "last_remote_online_count"
    private const val KEY_REMOTE_ONLINE_IDS = "remote_online_ids"
    private const val KEY_BRIDGE_POLICY_JSON = "bridge_policy_json"
    private const val KEY_ECONOMY_POLICY_JSON = "economy_policy_json"

    data class ServerSnapshot(
        val configured: Boolean,
        val endpoint: String,
        val lastAttempt: Long,
        val lastSuccess: Long,
        val lastError: String,
        val lastRemoteOnlineCount: Int
    ) {
        val connected: Boolean
            get() = configured && lastSuccess > 0L && lastSuccess >= lastAttempt
    }

    data class HeartbeatResult(
        val success: Boolean,
        val detail: String,
        val remoteOnlineCount: Int,
        val onlineContactIds: Set<String>
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun snapshot(context: Context): ServerSnapshot {
        val config = MeshServerConfigManager.current(context)
        return ServerSnapshot(
            configured = config.isConfigured,
            endpoint = config.endpointLabel,
            lastAttempt = prefs(context).getLong(KEY_LAST_ATTEMPT, 0L),
            lastSuccess = prefs(context).getLong(KEY_LAST_SUCCESS, 0L),
            lastError = prefs(context).getString(KEY_LAST_ERROR, "").orEmpty(),
            lastRemoteOnlineCount = prefs(context).getInt(KEY_LAST_REMOTE_ONLINE_COUNT, 0)
        )
    }

    fun cachedBridgePolicyJson(context: Context): String {
        return prefs(context).getString(KEY_BRIDGE_POLICY_JSON, "").orEmpty()
    }

    fun cachedEconomyPolicyJson(context: Context): String {
        return prefs(context).getString(KEY_ECONOMY_POLICY_JSON, "").orEmpty()
    }

    fun remoteOnlineIds(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_REMOTE_ONLINE_IDS, emptySet()) ?: emptySet()
    }

    suspend fun sendHeartbeat(
        context: Context,
        globalId: String,
        nodes: List<MeshNode>,
        contactCount: Int,
        remoteModeEnabled: Boolean
    ): HeartbeatResult = withContext(Dispatchers.IO) {
        val config = MeshServerConfigManager.current(context)
        if (!config.isConfigured) {
            return@withContext HeartbeatResult(
                success = false,
                detail = "Server belum diatur.",
                remoteOnlineCount = 0,
                onlineContactIds = emptySet()
            )
        }

        val now = System.currentTimeMillis()
        prefs(context).edit().putLong(KEY_LAST_ATTEMPT, now).apply()

        return@withContext runCatching {
            val payload =
                JSONObject().apply {
                    put("globalId", globalId)
                    put("remoteModeEnabled", remoteModeEnabled)
                    put("contactCount", contactCount)
                    put("localNodeCount", nodes.size)
                    put("timestamp", now)
                    put(
                        "nodes",
                        JSONArray().apply {
                            nodes.take(8).forEach { node ->
                                put(
                                    JSONObject().apply {
                                        put("name", node.name)
                                        put("ipAddress", node.ipAddress)
                                        put("signalStrength", node.signal)
                                        put("latency", node.latency)
                                        put("trustScore", node.trusted)
                                    }
                                )
                            }
                        }
                    )
                }

            val response =
                postJson(
                    url = "${config.baseUrl}/api/v1/presence/heartbeat",
                    apiKey = config.apiKey,
                    payload = payload.toString()
                )

            val json = JSONObject(response)
            val onlineIds =
                buildSet {
                    val array = json.optJSONArray("onlineContactIds") ?: JSONArray()
                    for (i in 0 until array.length()) {
                        val value = array.optString(i).trim()
                        if (value.isNotBlank()) add(value)
                    }
                }

            json.optJSONObject("bridgePolicy")?.let {
                prefs(context).edit().putString(KEY_BRIDGE_POLICY_JSON, it.toString()).apply()
            }
            json.optJSONObject("economyPolicy")?.let {
                prefs(context).edit().putString(KEY_ECONOMY_POLICY_JSON, it.toString()).apply()
            }

            prefs(context)
                .edit()
                .putLong(KEY_LAST_SUCCESS, now)
                .putString(KEY_LAST_ERROR, "")
                .putInt(KEY_LAST_REMOTE_ONLINE_COUNT, json.optInt("remoteOnlineCount", onlineIds.size))
                .putStringSet(KEY_REMOTE_ONLINE_IDS, onlineIds)
                .apply()

            HeartbeatResult(
                success = true,
                detail = json.optString("message", "Server tersambung."),
                remoteOnlineCount = json.optInt("remoteOnlineCount", onlineIds.size),
                onlineContactIds = onlineIds
            )
        }.getOrElse { error ->
            prefs(context)
                .edit()
                .putString(KEY_LAST_ERROR, error.message ?: "Server tidak merespon.")
                .apply()

            HeartbeatResult(
                success = false,
                detail = error.message ?: "Server tidak merespon.",
                remoteOnlineCount = prefs(context).getInt(KEY_LAST_REMOTE_ONLINE_COUNT, 0),
                onlineContactIds = remoteOnlineIds(context)
            )
        }
    }

    private fun postJson(
        url: String,
        apiKey: String,
        payload: String
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 8000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (apiKey.isNotBlank()) {
                setRequestProperty("X-Api-Key", apiKey)
            }
        }

        connection.outputStream.use { output ->
            output.write(payload.toByteArray(Charsets.UTF_8))
        }

        val code = connection.responseCode
        val stream =
            if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

        val body =
            BufferedReader(InputStreamReader(stream)).use { reader ->
                buildString {
                    var line = reader.readLine()
                    while (line != null) {
                        append(line)
                        line = reader.readLine()
                    }
                }
            }

        if (code !in 200..299) {
            throw IllegalStateException("Server error $code")
        }

        return body
    }
}
