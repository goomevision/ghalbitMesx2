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
    private const val HOST_UNHEALTHY_COOLDOWN_MS = 30_000L
    private const val ROUTE_LOCK_TTL_MS = 20_000L
    private const val ROUTE_LOCK_MAX_FAILURES = 3
    private const val SLOT_DIRECT_BOOST = 12
    private const val SLOT_RELAY_BOOST = 8
    private val lastDecisions = ConcurrentHashMap<String, AdaptiveRouteDecision>()
    private val switchHistory = ConcurrentHashMap<String, ArrayDeque<String>>()
    private val failedRouteCooldowns = ConcurrentHashMap<String, Long>()
    private val unhealthyHosts = ConcurrentHashMap<String, Long>()
    private val routeLocks = ConcurrentHashMap<String, RouteLock>()
    private val routeLockFailures = ConcurrentHashMap<String, Int>()

    fun evaluate(
        context: Context,
        chatId: String,
        globalId: String?,
        routeHint: String? = null,
        keepAliveState: ActiveConversationRouteState? = null
    ): AdaptiveRouteDecision {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        val activeLock = getActiveRouteLock(chatId, globalId, now)
        if (activeLock != null && !isInCooldown(now, chatId, globalId, activeLock.nextHop) && !isHostUnhealthy(now, activeLock.nextHop)) {
            val lockedDecision =
                AdaptiveRouteDecision(
                    chatId = chatId,
                    globalId = globalId,
                    routeType = activeLock.routeType,
                    transport = activeLock.transport,
                    nextHop = activeLock.nextHop,
                    reason = "routeLock:${activeLock.source.name.lowercase()}",
                    routeHealth = RouteHealthStatus.STABLE
                )
            lastDecisions[chatId] = lockedDecision
            Log.d(
                "GHALBIT-ROUTE-LOCK",
                "peer=${globalId ?: chatId} route=${activeLock.transport} source=${activeLock.source.name} ttlMs=${activeLock.expiresAt - now}"
            )
            return lockedDecision
        }

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
        if (success) {
            failedRouteCooldowns.remove(failureKeyByChat(chatId, nextHop))
            if (!globalId.isNullOrBlank()) {
                failedRouteCooldowns.remove(failureKeyByGlobal(globalId, nextHop))
            }
            unhealthyHosts.remove(nextHop)
            routeLockFailures[routeLockKey(chatId, globalId)] = 0
            lockRoute(chatId, globalId, nextHop, RouteEvidenceSource.CHAT)
            Log.d("GHALBIT-PREDICTIVE-ROUTE", "success chatId=$chatId nextHop=$nextHop")
        } else {
            val now = System.currentTimeMillis()
            failedRouteCooldowns[failureKeyByChat(chatId, nextHop)] = now
            if (!globalId.isNullOrBlank()) {
                failedRouteCooldowns[failureKeyByGlobal(globalId, nextHop)] = now
            }
            val lockKey = routeLockKey(chatId, globalId)
            val failures = (routeLockFailures[lockKey] ?: 0) + 1
            routeLockFailures[lockKey] = failures
            if (failures >= ROUTE_LOCK_MAX_FAILURES) {
                routeLocks.remove(lockKey)
                routeLockFailures[lockKey] = 0
                Log.w("GHALBIT-ROUTE-LOCK", "release peer=${globalId ?: chatId} reason=consecutiveFailure count=$failures")
            }
            Log.d("GHALBIT-PREDICTIVE-ROUTE", "cooldown chatId=$chatId nextHop=$nextHop")
        }
    }

    fun markHostTemporarilyUnhealthy(host: String, reason: String) {
        if (host.isBlank()) return
        unhealthyHosts[host] = System.currentTimeMillis()
        routeLocks.entries.removeIf { it.value.nextHop == host }
        Log.d("GHALBIT-PREDICTIVE-ROUTE", "hostUnhealthy host=$host reason=$reason cooldownMs=$HOST_UNHEALTHY_COOLDOWN_MS")
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
        val preferredTransport = RouteTimeSlotScheduler.getPreferredTransport(now)
        val neighborSlots = RouteTimeSlotScheduler.getNeighborSlots(now)
        val boostApplied = preferredTransport == "LOCAL_MESH_DIRECT" || preferredTransport == "LOCAL_RELAY"
        val filtered = candidates
            .distinctBy { it.nextHopId }
            .filterNot { hint ->
                val cooled = isInCooldown(now, chatId, globalId, hint.nextHopId) || isHostUnhealthy(now, hint.nextHopId)
                if (cooled) Log.d("GHALBIT-PREDICTIVE-ROUTE", "skipCooldown chatId=$chatId nextHop=${hint.nextHopId}")
                cooled
            }
            .sortedByDescending { hint ->
                val base = IntelligentRouteMemory.scoreHint(hint).score
                val slotBoost = slotBoostForHint(hint, preferredTransport, neighborSlots)
                base + slotBoost
            }

        Log.d(
            "GHALBIT-ROUTE-SLOT",
            "peer=${globalId ?: chatId} preferred=$preferredTransport boosted=$boostApplied"
        )
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

    private fun isInCooldown(now: Long, chatId: String, globalId: String?, nextHop: String): Boolean {
        val chatLast = failedRouteCooldowns[failureKeyByChat(chatId, nextHop)] ?: 0L
        val globalLast = if (!globalId.isNullOrBlank()) failedRouteCooldowns[failureKeyByGlobal(globalId, nextHop)] ?: 0L else 0L
        val latest = maxOf(chatLast, globalLast)
        return now - latest < FAILED_ROUTE_COOLDOWN_MS
    }

    private fun failureKeyByChat(chatId: String, nextHop: String): String = "chat:$chatId@$nextHop"

    private fun failureKeyByGlobal(globalId: String, nextHop: String): String = "global:$globalId@$nextHop"

    private fun isHostUnhealthy(now: Long, host: String): Boolean {
        val last = unhealthyHosts[host] ?: return false
        val active = now - last < HOST_UNHEALTHY_COOLDOWN_MS
        if (!active) unhealthyHosts.remove(host)
        return active
    }

    private fun slotBoostForHint(hint: RouteHint, preferredTransport: String, neighborSlots: List<Int>): Int {
        val isDirect = hint.hopCount <= 1
        val currentSlot = neighborSlots.firstOrNull() ?: 0
        val hintSlot = if (isDirect) 0 else 1
        return when (preferredTransport) {
            "LOCAL_MESH_DIRECT" -> {
                when {
                    isDirect && currentSlot == 0 -> SLOT_DIRECT_BOOST
                    isDirect && hintSlot in neighborSlots -> SLOT_DIRECT_BOOST / 2
                    else -> 0
                }
            }
            "LOCAL_RELAY" -> {
                when {
                    !isDirect && currentSlot == 1 -> SLOT_RELAY_BOOST
                    !isDirect && hintSlot in neighborSlots -> SLOT_RELAY_BOOST / 2
                    else -> 0
                }
            }
            else -> 0
        }
    }

    private fun getActiveRouteLock(chatId: String, globalId: String?, now: Long): RouteLock? {
        val keys = listOf(routeLockKey(chatId, null), routeLockKey(chatId, globalId))
        keys.forEach { key ->
            val lock = routeLocks[key] ?: return@forEach
            if (lock.expiresAt <= now) {
                routeLocks.remove(key)
                return@forEach
            }
            return lock
        }
        return null
    }

    private fun lockRoute(chatId: String, globalId: String?, nextHop: String, source: RouteEvidenceSource) {
        val decision = lastDecisions[chatId] ?: return
        if (decision.routeType == AdaptiveRouteType.PENDING_QUEUE || nextHop.isBlank()) return
        val now = System.currentTimeMillis()
        val lock =
            RouteLock(
                chatId = chatId,
                globalId = globalId,
                nextHop = nextHop,
                transport = decision.transport,
                routeType = decision.routeType,
                source = source,
                lockedAt = now,
                expiresAt = now + ROUTE_LOCK_TTL_MS
            )
        routeLocks[routeLockKey(chatId, globalId)] = lock
        Log.d("GHALBIT-ROUTE-LOCK", "peer=${globalId ?: chatId} route=${decision.transport} source=${source.name} ttlMs=$ROUTE_LOCK_TTL_MS")
    }

    private fun routeLockKey(chatId: String, globalId: String?): String = "${chatId.trim()}|${globalId?.trim().orEmpty()}"

    private data class RouteLock(
        val chatId: String,
        val globalId: String?,
        val nextHop: String,
        val transport: String,
        val routeType: AdaptiveRouteType,
        val source: RouteEvidenceSource,
        val lockedAt: Long,
        val expiresAt: Long
    )
}
