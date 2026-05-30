package com.ghalbitnet.meshx2.online

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.BuildConfig
import java.util.concurrent.ConcurrentHashMap

object RelayRegistryManager {
    private const val PREFS = "ghalbit_relay_registry"
    private const val KEY_RELAYS = "relays"

    data class RelayEndpoint(
        val url: String,
        val role: String,
        val priority: Int
    )

    private val failures = ConcurrentHashMap<String, Int>()
    private val latencies = ConcurrentHashMap<String, Long>()

    fun all(context: Context): List<RelayEndpoint> {
        val configured = prefs(context).getString(KEY_RELAYS, null)
            ?.split(",")
            ?.mapNotNull { it.trim().takeIf(String::isNotBlank) }
            .orEmpty()
        val defaults = listOf(BuildConfig.BASE_RELAY_URL).filter { it.isNotBlank() }
        return (configured + defaults)
            .distinct()
            .mapIndexed { index, url ->
                RelayEndpoint(url = url.trimEnd('/'), role = if (index == 0) "PRIMARY" else "COMMUNITY", priority = index + 1)
            }
            .sortedWith(compareBy<RelayEndpoint> { failures[it.url] ?: 0 }.thenBy { it.priority })
    }

    fun current(context: Context): RelayEndpoint? = all(context).firstOrNull()

    fun markSuccess(url: String, latencyMs: Long) {
        failures.remove(url)
        latencies[url] = latencyMs
    }

    fun markFailure(url: String) {
        val count = (failures[url] ?: 0) + 1
        failures[url] = count
        if (count >= 2) {
            Log.w("GHALBIT-RELAY", "failover activated url=$url")
        }
    }

    fun lastLatency(url: String): Long = latencies[url] ?: 0L

    fun saveRelays(context: Context, relays: List<String>) {
        prefs(context).edit().putString(KEY_RELAYS, relays.joinToString(",")).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
