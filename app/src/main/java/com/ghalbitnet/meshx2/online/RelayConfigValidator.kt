package com.ghalbitnet.meshx2.online

import android.content.Context
import android.os.Build
import android.util.Log
import com.ghalbitnet.meshx2.BuildConfig
import com.ghalbitnet.meshx2.util.LogThrottle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class RelayConfigValidation(
    val state: State,
    val relayUrl: String,
    val presenceUrl: String,
    val detail: String,
    val checkedAt: Long = System.currentTimeMillis()
) {
    enum class State {
        INTERNET_RELAY_NOT_CONFIGURED,
        INTERNET_RELAY_UNREACHABLE,
        INTERNET_RELAY_READY
    }
}

object RelayConfigValidator {
    private const val PREFS = "ghalbit_relay_validator"
    private const val KEY_STATE = "state"
    private const val KEY_DETAIL = "detail"
    private const val KEY_CHECKED_AT = "checkedAt"
    private const val LOG_WINDOW_MS = 30_000L
    private var lastLogAt = 0L

    fun cached(context: Context): RelayConfigValidation {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val state =
            RelayConfigValidation.State.entries.firstOrNull {
                it.name == prefs.getString(KEY_STATE, RelayConfigValidation.State.INTERNET_RELAY_NOT_CONFIGURED.name)
            } ?: RelayConfigValidation.State.INTERNET_RELAY_NOT_CONFIGURED
        return RelayConfigValidation(
            state = state,
            relayUrl = BuildConfig.BASE_RELAY_URL.trim(),
            presenceUrl = BuildConfig.BASE_PRESENCE_URL.trim(),
            detail = prefs.getString(KEY_DETAIL, "Belum diperiksa").orEmpty(),
            checkedAt = prefs.getLong(KEY_CHECKED_AT, 0L)
        )
    }

    suspend fun validate(context: Context, force: Boolean = false): RelayConfigValidation =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val cached = cached(context)
            if (!force && now - cached.checkedAt < LOG_WINDOW_MS && cached.relayUrl == BuildConfig.BASE_RELAY_URL.trim()) {
                return@withContext cached
            }

            val relayUrl = BuildConfig.BASE_RELAY_URL.trim().trimEnd('/')
            val presenceUrl = BuildConfig.BASE_PRESENCE_URL.trim().ifBlank { relayUrl }.trimEnd('/')
            val result =
                when {
                    relayUrl.isBlank() || presenceUrl.isBlank() -> {
                        throttledLog("GHALBIT-RELAY-CONFIG", "missing relayUrl")
                        RelayConfigValidation(
                            state = RelayConfigValidation.State.INTERNET_RELAY_NOT_CONFIGURED,
                            relayUrl = relayUrl,
                            presenceUrl = presenceUrl,
                            detail = "Relay internet belum dikonfigurasi"
                        )
                    }
                    isInvalidLocalhostOnDevice(relayUrl) || isInvalidLocalhostOnDevice(presenceUrl) -> {
                        throttledLog("GHALBIT-RELAY-CONFIG", "invalid localhost on device")
                        RelayConfigValidation(
                            state = RelayConfigValidation.State.INTERNET_RELAY_UNREACHABLE,
                            relayUrl = relayUrl,
                            presenceUrl = presenceUrl,
                            detail = "URL localhost tidak valid di perangkat fisik"
                        )
                    }
                    healthOk(relayUrl) -> {
                        throttledLog("GHALBIT-RELAY-CONFIG", "health check ok")
                        throttledLog("GHALBIT-RELAY-CONFIG", "ready")
                        RelayConfigValidation(
                            state = RelayConfigValidation.State.INTERNET_RELAY_READY,
                            relayUrl = relayUrl,
                            presenceUrl = presenceUrl,
                            detail = "Relay internet siap"
                        )
                    }
                    else -> {
                        throttledLog("GHALBIT-RELAY-CONFIG", "unreachable")
                        RelayConfigValidation(
                            state = RelayConfigValidation.State.INTERNET_RELAY_UNREACHABLE,
                            relayUrl = relayUrl,
                            presenceUrl = presenceUrl,
                            detail = "Relay internet tidak dapat dijangkau"
                        )
                    }
                }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STATE, result.state.name)
                .putString(KEY_DETAIL, result.detail)
                .putLong(KEY_CHECKED_AT, result.checkedAt)
                .apply()
            result
        }

    private fun isInvalidLocalhostOnDevice(url: String): Boolean {
        val normalized = url.lowercase()
        val isLocalhost = normalized.contains("://localhost") || normalized.contains("://127.0.0.1") || normalized.contains("://0.0.0.0")
        if (!isLocalhost) return false
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val emulator = fingerprint.contains("generic") || fingerprint.contains("emulator") || model.contains("sdk")
        return !emulator
    }

    private fun healthOk(relayUrl: String): Boolean {
        return runCatching {
            val connection = (URL("${relayUrl.trimEnd('/')}/health").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
            }
            val ok = connection.responseCode in 200..299
            connection.disconnect()
            ok
        }.getOrDefault(false)
    }

    private fun throttledLog(tag: String, message: String) {
        val now = System.currentTimeMillis()
        if (now - lastLogAt >= LOG_WINDOW_MS) {
            LogThrottle.d(tag, "relay-config:$message", message, LOG_WINDOW_MS)
            lastLogAt = now
        }
    }
}
