package com.ghalbitnet.meshx2.chat

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.core.node.NodeStatusManager
import com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager
import com.ghalbitnet.meshx2.online.OnlinePresenceManager
import com.ghalbitnet.meshx2.routing.IntelligentRouteMemory
import com.ghalbitnet.meshx2.routing.RouteHint
import java.util.concurrent.ConcurrentHashMap

object AdaptiveRouteManager {
    private const val FAILED_ROUTE_COOLDOWN_MS = 30_000L
    private val lastDecisions = ConcurrentHashMap<String, AdaptiveRouteDecision>()
    private val switchHistory = ConcurrentHashMap<String, ArrayDeque<String>>()
    private val failedRouteCooldowns = ConcurrentHashMap<String, Long>()

    fun evaluate(
        context: Context,
        chatId: String,
        globalId: String?,
        routeHint: String? = null,
        keepAliveState: ActiveConversationRouteState? = null
    ): AdaptiveRouteDecision {
        val appContext = context.applicationContext
        val candidates = routeCandidates(appContext, chatId, globalId, routeHint, keepAliveState)
        val localHint = candidates.firstOrNull()
        val internetRoute = globalId?.let { OnlinePresenceManager.getOnlineRoute(appContext, it) }
        val liveNode =
            NodeStatusManager.getOnlineNodes().firstOrNull {
                it.online && (it.name == chatId || (!globalId.isNullOrBlank() && it.name == globalId))
            }

        val decision =
            when {
                keepAliveState != null &&
                    keepAliveState.routeHealth == RouteHealthStatus.INTERNET_FALLBACK &&
                    internetRoute != null -> {
                    AdaptiveRouteDecision(
                        chatId = chatId,
                        globalId = globalId,
                        routeType = AdaptiveRouteType.INTERNET_RELAY,
                        transport = "INTERNET_RELAY",
                        nextHop = internetRoute.relayUrl,
                        reason = "keepaliveInternetFallback",
                        routeHealth = RouteHealthStatus.INTERNET_FALLBACK
                    )
                }

                localHint != null && localHint.hopCount <= 1 -> {
                    AdaptiveRouteDecision(
                        chatId = chatId,
                        globalId = globalId,
                        routeType = AdaptiveRouteType.LOCAL_MESH_DIRECT,
                        transport = "LOCAL_MESH_DIRECT",
                        nextHop = localHint.nextHopId,
                        reason = if (localHint.nextHopId == routeHint) "directHint" else "predictiveDirectHint",
                        routeHealth =
                            when (keepAliveState?.routeHealth) {
                                RouteHealthStatus.WEAK -> RouteHealthStatus.WEAK
                                RouteHealthStatus.PROBING_ROUTE -> RouteHealthStatus.PROBING_ROUTE
                                RouteHealthStatus.RECONNECTING -> RouteHealthStatus.RECONNECTING
                                else -> RouteHealthStatus.STABLE
                            }
                    )
                }

                localHint != null -> {
                    AdaptiveRouteDecision(
                        chatId = chatId,
                        globalId = globalId,
                        routeType = AdaptiveRouteType.LOCAL_RELAY,
                        transport = "LOCAL_RELAY",
                        nextHop = localHint.nextHopId,
                        reason = if (localHint.nextHopId == routeHint) "relayHint" else "predictiveRelayHint",
                        routeHealth = keepAliveState?.routeHealth ?: RouteHealthStatus.WEAK
                    )
                }

                liveNode != null && MeshRuntimeManager.activeTransports().contains("Nearby") -> {
                    AdaptiveRouteDecision(
                        chatId = chatId,
                        globalId = globalId,
                        routeType = AdaptiveRouteType.NEARBY,
                        transport = "NEARBY",
                        nextHop = liveNode.ipAddress,
                        reason = "nearbyFallback",
                        routeHealth = RouteHealthStatus.WEAK
                    )
                }

                internetRoute != null && OnlinePresenceManager.hasInternet(appContext) -> {
                    AdaptiveRouteDecision(
                        chatId = chatId,
                        globalId = globalId,
                        routeType = AdaptiveRouteType.INTERNET_RELAY,
                        transport = "INTERNET_RELAY",
                        nextHop = internetRoute.relayUrl,
                        reason = "presenceOnline",
                        routeHealth = RouteHealthStatus.INTERNET_FALLBACK
                    )
                }

                else -> {
                    AdaptiveRouteDecision(
                        chatId = chatId,
                        globalId = globalId,
                        routeType = AdaptiveRouteType.PENDING_QUEUE,
                        transport = "PENDING_QUEUE",
                        nextHop = null,
                        reason = "noHealthyRoute",
                        routeHealth = RouteHealthStatus.OFFLINE_PENDING
                    )
                }
            }

        val previous = lastDecisions.put(chatId, decision)
        if (previous == null || previous.routeType != decision.routeType || previous.nextHop != decision.nextHop) {
            val message =
                "target=${globalId ?: chatId} ${previous?.routeType?.name ?: "NONE"} -> ${decision.routeType.name} nextHop=${decision.nextHop ?: "-"}"
            rememberSwitch(chatId, message)
            Log.d("GHALBIT-ROUTE-SWITCH", message)
        }
        Log.d(
            "GHALBIT-ADAPTIVE-ROUTE",
            "target=${globalId ?: chatId} route=${decision.routeType.name} transport=${decision.transport} reason=${decision.reason} nextHop=${decision.nextHop ?: "-"}"
        )
        return decision
    }

    fun markRouteResult(chatId: String, globalId: String?, nextHop: String?, success: Boolean) {
        if (nextHop.isNullOrBlank()) return
        val key = failureKey(chatId, globalId, nextHop)
        if (success) {
            failedRouteCooldowns.remove(key)
            Log.d("GHALBIT-PREDICTIVE-ROUTE", "success chatId=$chatId nextHop=$nextHop")
        } else {
            failedRouteCooldowns[key] = System.currentTimeMillis()
            Log.d("GHALBIT-PREDICTIVE-ROUTE", "cooldown chatId=$chatId nextHop=$nextHop")
        }
    }

    fun activeRoutes(): List<AdaptiveRouteDecision> = lastDecisions.values.sortedBy { it.chatId }

    fun switchHistory(chatId: String? = null): List<String> {
        return if (chatId != null) {
            switchHistory[chatId]?.toList().orEmpty()
        } else {
            switchHistory.values.flatMap { it.toList() }.takeLast(12)
        }
    }

    fun contactStatusLabel(
        context: Context,
        contact: LiveContactItem,
        keepAliveState: ActiveConversationRouteState? = null,
        pending: Boolean = false
    ): String {
        val decision = evaluate(context, contact.chatId, contact.globalId, contact.routeHint, keepAliveState)
        return when {
            contact.isLive && contact.verificationStatus == PeerVerificationStatus.PROVISIONAL -> "Peer belum terverifikasi"
            pending && decision.routeType == AdaptiveRouteType.PENDING_QUEUE -> "Offline Pending"
            decision.routeType == AdaptiveRouteType.LOCAL_RELAY -> "Relay Mesh"
            decision.routeType == AdaptiveRouteType.LOCAL_MESH_DIRECT -> "Local Mesh"
            decision.routeType == AdaptiveRouteType.NEARBY -> "Weak Signal"
            decision.routeType == AdaptiveRouteType.INTERNET_RELAY -> "Internet Online"
            keepAliveState?.routeHealth == RouteHealthStatus.RECONNECTING -> "Reconnecting"
            else -> "Offline Pending"
        }
    }

    private fun routeCandidates(
        context: Context,
        chatId: String,
        globalId: String?,
        routeHint: String?,
        keepAliveState: ActiveConversationRouteState?
    ): List<RouteHint> {
        val ids = listOfNotNull(globalId, chatId).distinct()
        val candidates = mutableListOf<RouteHint>()
        if (!routeHint.isNullOrBlank()) {
            candidates += RouteHint(
                destinationId = globalId ?: chatId,
                nextHopId = routeHint,
                latencyMs = keepAliveState?.latencyMs?.takeIf { it > 0 } ?: 0L,
                hopCount = 1,
                trustScore = if (keepAliveState?.routeHealth == RouteHealthStatus.STABLE) 85 else 60,
                lastSeen = System.currentTimeMillis()
            )
        }
        ids.forEach { id -> candidates += IntelligentRouteMemory.getCandidateHints(context, id) }
        val now = System.currentTimeMillis()
        val filtered = candidates
            .distinctBy { it.nextHopId }
            .filterNot { hint ->
                val cooled = now - (failedRouteCooldowns[failureKey(chatId, globalId, hint.nextHopId)] ?: 0L) < FAILED_ROUTE_COOLDOWN_MS
                if (cooled) Log.d("GHALBIT-PREDICTIVE-ROUTE", "skipCooldown chatId=$chatId nextHop=${hint.nextHopId}")
                cooled
            }
            .sortedByDescending { IntelligentRouteMemory.scoreHint(it).score }
        Log.d(
            "GHALBIT-PREDICTIVE-ROUTE",
            "candidates chatId=$chatId count=${filtered.size} list=${filtered.joinToString { "${it.nextHopId}:${IntelligentRouteMemory.scoreHint(it).score}" }}"
        )
        return filtered
    }

    private fun rememberSwitch(chatId: String, message: String) {
        val history = switchHistory.getOrPut(chatId) { ArrayDeque() }
        history.addLast(message)
        while (history.size > 8) {
            history.removeFirst()
        }
    }

    private fun failureKey(chatId: String, globalId: String?, nextHop: String): String = "${globalId ?: chatId}@$nextHop"
}
