package com.ghalbitnet.meshx2.routing

import com.ghalbitnet.meshx2.chat.RouteHealthStatus
import com.ghalbitnet.meshx2.ui.RuntimeUiState
import java.util.concurrent.ConcurrentHashMap

data class ActiveRouteSearchState(
    val key: String,
    val state: RouteSearchState,
    val label: String,
    val expiresAt: Long
)

object RouteStateReconciler {
    private val activeStates = ConcurrentHashMap<String, ActiveRouteSearchState>()

    fun keysFor(chatId: String, globalId: String?): List<String> {
        return listOfNotNull(chatId.takeIf { it.isNotBlank() }, globalId?.takeIf { it.isNotBlank() }).distinct()
    }

    fun mark(keys: Collection<String>, state: RouteSearchState, label: String, holdMs: Long = holdDuration(state)) {
        if (keys.isEmpty()) return
        val expiresAt = System.currentTimeMillis() + holdMs
        keys.forEach { key ->
            activeStates[key] = ActiveRouteSearchState(key = key, state = state, label = label, expiresAt = expiresAt)
        }
    }

    fun clear(keys: Collection<String>) {
        keys.forEach { activeStates.remove(it) }
    }

    fun current(chatId: String? = null, globalId: String? = null): ActiveRouteSearchState? {
        purgeExpired()
        return listOfNotNull(chatId, globalId)
            .mapNotNull { activeStates[it] }
            .maxByOrNull { it.expiresAt }
    }

    fun activeGlobalState(): ActiveRouteSearchState? {
        purgeExpired()
        return activeStates.values.maxByOrNull { it.expiresAt }
    }

    fun reconcileHealth(chatId: String?, globalId: String?, proposed: RouteHealthStatus): RouteHealthStatus {
        val active = current(chatId, globalId) ?: return proposed
        if (!isProtected(active.state)) return proposed
        return when {
            proposed == RouteHealthStatus.OFFLINE_PENDING -> RouteHealthStatus.PROBING_ROUTE
            proposed == RouteHealthStatus.RECONNECTING -> RouteHealthStatus.PROBING_ROUTE
            else -> proposed
        }
    }

    fun preferredTransport(chatId: String?, globalId: String?): String? {
        val active = current(chatId, globalId) ?: return null
        if (!isProtected(active.state)) return null
        return active.state.name
    }

    fun preferredRouteLabel(chatId: String?, globalId: String?): String? {
        val active = current(chatId, globalId) ?: return null
        if (!isProtected(active.state)) return null
        return active.label
    }

    fun shouldSuppressPending(chatId: String?, globalId: String?): Boolean {
        val active = current(chatId, globalId) ?: return false
        return isProtected(active.state)
    }

    fun runtimeState(active: ActiveRouteSearchState): RuntimeUiState {
        return when (active.state) {
            RouteSearchState.SEARCHING_SERVER,
            RouteSearchState.SEARCHING_INTERNET_RELAY,
            RouteSearchState.SEARCHING_LOCAL_MESH_PRIMARY,
            RouteSearchState.SEARCHING_LOCAL_MESH_SECONDARY,
            RouteSearchState.SEARCHING_COPY_NODES -> RuntimeUiState.DISCOVERING
            RouteSearchState.ROUTE_PROBING,
            RouteSearchState.ROUTE_SWITCHING -> RuntimeUiState.CONNECTING
            RouteSearchState.ROUTE_FOUND -> RuntimeUiState.READY
            RouteSearchState.ROUTE_LOST,
            RouteSearchState.FAILED,
            RouteSearchState.IDLE -> RuntimeUiState.OFFLINE_PENDING
        }
    }

    private fun holdDuration(state: RouteSearchState): Long {
        return when (state) {
            RouteSearchState.ROUTE_SWITCHING,
            RouteSearchState.ROUTE_PROBING -> 4_000L
            RouteSearchState.ROUTE_FOUND -> 2_500L
            else -> 8_000L
        }
    }

    private fun isProtected(state: RouteSearchState): Boolean {
        return state == RouteSearchState.SEARCHING_SERVER ||
            state == RouteSearchState.SEARCHING_LOCAL_MESH_PRIMARY ||
            state == RouteSearchState.SEARCHING_LOCAL_MESH_SECONDARY ||
            state == RouteSearchState.SEARCHING_INTERNET_RELAY ||
            state == RouteSearchState.SEARCHING_COPY_NODES ||
            state == RouteSearchState.ROUTE_PROBING ||
            state == RouteSearchState.ROUTE_SWITCHING
    }

    private fun purgeExpired() {
        val now = System.currentTimeMillis()
        activeStates.entries.removeIf { now >= it.value.expiresAt }
    }
}
