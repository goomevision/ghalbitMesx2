package com.ghalbitnet.meshx2.economy

import android.content.Context
import org.json.JSONObject

object InternetGatewayLoadManager {

    data class GatewayLoadSnapshot(
        val gatewayId: String,
        val activeLoad: Int
    )

    private const val PREFS_NAME = "internet_gateway_load_manager"
    private const val KEY_COUNTS = "active_counts"

    fun activeLoad(
        context: Context,
        gatewayId: String
    ): Int {
        if (gatewayId.isBlank()) return 0
        return loadMap(context).optInt(gatewayId, 0).coerceAtLeast(0)
    }

    fun reserve(
        context: Context,
        gatewayId: String
    ) {
        if (gatewayId.isBlank()) return
        val map = loadMap(context)
        map.put(gatewayId, activeLoad(context, gatewayId) + 1)
        save(context, map)
    }

    fun release(
        context: Context,
        gatewayId: String
    ) {
        if (gatewayId.isBlank()) return
        val map = loadMap(context)
        val next = (map.optInt(gatewayId, 0) - 1).coerceAtLeast(0)
        if (next == 0) {
            map.remove(gatewayId)
        } else {
            map.put(gatewayId, next)
        }
        save(context, map)
    }

    fun snapshot(
        context: Context
    ): List<GatewayLoadSnapshot> {
        val map = loadMap(context)
        return buildList {
            val keys = map.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                add(
                    GatewayLoadSnapshot(
                        gatewayId = key,
                        activeLoad = map.optInt(key, 0).coerceAtLeast(0)
                    )
                )
            }
        }.sortedByDescending { it.activeLoad }
    }

    private fun loadMap(context: Context): JSONObject {
        val raw =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_COUNTS, "{}")
                .orEmpty()
        return runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
    }

    private fun save(
        context: Context,
        source: JSONObject
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COUNTS, source.toString())
            .apply()
    }
}
