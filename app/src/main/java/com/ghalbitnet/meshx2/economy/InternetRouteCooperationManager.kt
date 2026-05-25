package com.ghalbitnet.meshx2.economy

import android.content.Context
import com.ghalbitnet.meshx2.model.MeshNode
import org.json.JSONObject

object InternetRouteCooperationManager {

    data class RouteLoadSnapshot(
        val routeKey: String,
        val activeLoad: Int
    )

    private const val PREFS_NAME = "internet_route_cooperation"
    private const val KEY_ACTIVE = "active_route_counts"

    fun choosePlan(
        context: Context,
        nodes: List<MeshNode>
    ): InternetRoutePlanner.RoutePlan? {
        val plans =
            InternetRoutePlanner.plans(context, nodes)
        if (plans.isEmpty()) {
            return null
        }

        val bestScore = plans.first().routeScore
        val cooperativePool =
            plans
                .filter { bestScore - it.routeScore <= 8 }
                .take(3)

        return cooperativePool.minWithOrNull(
            compareBy<InternetRoutePlanner.RoutePlan> { activeLoad(context, it.routeKey) }
                .thenBy { gatewayLoad(context, it) }
                .thenByDescending { it.routeScore }
        ) ?: plans.first()
    }

    fun reserve(
        context: Context,
        routeKey: String
    ) {
        if (routeKey.isBlank()) return
        val map = loadMap(context)
        map.put(routeKey, activeLoad(context, routeKey) + 1)
        save(context, map)
    }

    fun release(
        context: Context,
        routeKey: String
    ) {
        if (routeKey.isBlank()) return
        val map = loadMap(context)
        val next = (map.optInt(routeKey, 0) - 1).coerceAtLeast(0)
        if (next == 0) {
            map.remove(routeKey)
        } else {
            map.put(routeKey, next)
        }
        save(context, map)
    }

    fun activeLoad(
        context: Context,
        routeKey: String
    ): Int {
        return loadMap(context).optInt(routeKey, 0).coerceAtLeast(0)
    }

    fun snapshot(
        context: Context
    ): List<RouteLoadSnapshot> {
        val map = loadMap(context)
        return buildList {
            val keys = map.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                add(
                    RouteLoadSnapshot(
                        routeKey = key,
                        activeLoad = map.optInt(key, 0).coerceAtLeast(0)
                    )
                )
            }
        }.sortedByDescending { it.activeLoad }
    }

    private fun gatewayLoad(
        context: Context,
        plan: InternetRoutePlanner.RoutePlan
    ): Int {
        return InternetGatewayLoadManager.activeLoad(context, plan.gateway.nodeId)
    }

    private fun loadMap(context: Context): JSONObject {
        val raw =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ACTIVE, "{}")
                .orEmpty()
        return runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
    }

    private fun save(
        context: Context,
        source: JSONObject
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE, source.toString())
            .apply()
    }
}
