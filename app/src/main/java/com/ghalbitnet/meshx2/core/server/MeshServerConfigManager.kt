package com.ghalbitnet.meshx2.core.server

import android.content.Context

object MeshServerConfigManager {

    private const val PREFS_NAME = "mesh_server_config"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_API_KEY = "api_key"

    data class Config(
        val baseUrl: String,
        val apiKey: String
    ) {
        val isConfigured: Boolean
            get() = baseUrl.isNotBlank()

        val endpointLabel: String
            get() = if (baseUrl.isBlank()) "-" else baseUrl
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun current(context: Context): Config {
        return Config(
            baseUrl = prefs(context).getString(KEY_BASE_URL, "").orEmpty(),
            apiKey = prefs(context).getString(KEY_API_KEY, "").orEmpty()
        )
    }

    fun save(
        context: Context,
        baseUrl: String,
        apiKey: String
    ) {
        prefs(context)
            .edit()
            .putString(KEY_BASE_URL, normalizeUrl(baseUrl))
            .putString(KEY_API_KEY, apiKey.trim())
            .apply()
    }

    private fun normalizeUrl(url: String): String {
        return url.trim().removeSuffix("/")
    }
}
