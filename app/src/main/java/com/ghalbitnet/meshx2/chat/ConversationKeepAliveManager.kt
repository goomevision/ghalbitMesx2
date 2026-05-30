package com.ghalbitnet.meshx2.chat

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.MainActivity
import com.ghalbitnet.meshx2.call.CallManager
import com.ghalbitnet.meshx2.core.runtime.PacketTraceEntry
import com.ghalbitnet.meshx2.core.runtime.PacketTraceStore
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.network.MeshSocketClient
import com.ghalbitnet.meshx2.online.OnlineFallbackTransport
import com.ghalbitnet.meshx2.online.OnlinePresenceManager
import com.ghalbitnet.meshx2.routing.IntelligentRouteMemory
import com.ghalbitnet.meshx2.routing.RouteStateReconciler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

object ConversationKeepAliveManager {
    private const val MAX_PROBING_FAILURES = 5
    private const val FRESH_HINT_WINDOW_MS = 30_000L
    private const val FAST_PING_MS = 3_000L
    private const val NORMAL_PING_MS = 8_000L
    private const val STABLE_LOW_POWER_PING_MS = 15_000L
    private const val WEAK_SIGNAL_PING_MS = 4_000L
    private const val MINIMAL_SIGNAL_PING_MS = 2_500L
    private const val PONG_TIMEOUT_MS = 18_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val states = ConcurrentHashMap<String, ActiveConversationRouteState>()
    private val misses = ConcurrentHashMap<String, Int>()
    private val reconnectCounters = ConcurrentHashMap<String, Int>()
    private val latencyHistory = ConcurrentHashMap<String, ArrayDeque<Long>>()
    private val _stateFlow = MutableStateFlow<Map<String, ActiveConversationRouteState>>(emptyMap())
    val stateFlow: StateFlow<Map<String, ActiveConversationRouteState>> = _stateFlow

    private data class SessionConfig(
        val chatId: String,
        val globalId: String?,
        val routeHint: String?,
        val preferFastPing: Boolean
    )

    private val configs = ConcurrentHashMap<String, SessionConfig>()

    fun startConversation(
        context: Context,
        chatId: String,
        globalId: String?,
        routeHint: String?,
        preferFastPing: Boolean = false
    ) {
        val appContext = context.applicationContext
        configs[chatId] = SessionConfig(chatId, globalId, routeHint, preferFastPing)
        if (jobs[chatId]?.isActive == true) {
            Log.d("GHALBIT-KEEPALIVE", "already active chatId=$chatId")
            return
        }
        Log.d("GHALBIT-KEEPALIVE", "start chatId=$chatId globalId=${globalId ?: "-"} route=${routeHint ?: "-"}")
        jobs[chatId] =
            scope.launch {
                while (true) {
                    sendKeepAlive(appContext, chatId)
                    val interval = nextKeepAliveDelay(chatId, configs[chatId])
                    Log.d("GHALBIT-LOW-SIGNAL", "keepaliveInterval chatId=$chatId intervalMs=$interval health=${states[chatId]?.routeHealth ?: "-"} misses=${misses[chatId] ?: 0}")
                    delay(interval)
                }
            }
    }

    fun stopConversation(chatId: String) {
        jobs.remove(chatId)?.cancel()
        configs.remove(chatId)
        Log.d("GHALBIT-KEEPALIVE", "stop chatId=$chatId")
    }

    fun snapshot(chatId: String): ActiveConversationRouteState? = stateFlow.value[chatId]

    fun isRouteStaleAfterConfirmation(chatId: String, routeHint: String?): Boolean {
        val route = routeHint ?: return false
        val state = states[chatId] ?: return false
        return state.activeRoute == route &&
            state.packetLossEstimate >= 100 &&
            (misses[chatId] ?: 0) >= MAX_PROBING_FAILURES
    }

    fun onPacketReceived(context: Context, packet: MeshPacket, payload: String) {
        when (packet.type) {
            "PING" -> handlePing(context.applicationContext, packet, payload)
            "PONG" -> handlePong(packet, payload)
            "ROUTE_CHECK" -> handleRouteCheck(context.applicationContext, packet, payload)
        }
    }

    private fun sendKeepAlive(context: Context, chatId: String) {
        val config = configs[chatId] ?: return
        val decision =
            AdaptiveRouteManager.evaluate(
                context = context,
                chatId = chatId,
                globalId = config.globalId,
                routeHint = config.routeHint,
                keepAliveState = states[chatId]
            )
        val route = decision.nextHop
        if (decision.routeType == AdaptiveRouteType.INTERNET_RELAY) {
            sendInternetKeepAlive(config, decision)
            return
        }
        if (route.isNullOrBlank()) {
            switchToFallback(context, config, "missingLocalRoute")
            return
        }

        val currentMisses = misses[chatId] ?: 0
        val packetType = if (currentMisses >= 1 || states[chatId]?.routeHealth == RouteHealthStatus.PROBING_ROUTE) "ROUTE_CHECK" else "PING"
        val sentAt = System.currentTimeMillis()
        val payload =
            JSONObject()
                .put("chatId", chatId)
                .put("sourceNodeId", MainActivity.myGlobalPeerId)
                .put("sourceGlobalId", config.globalId)
                .put("sentAt", sentAt)
                .put("minimalSignal", currentMisses >= 2)
                .toString()
        val packet =
            MeshPacket(
                packetId = "$packetType-$sentAt",
                source = MainActivity.myGlobalPeerId,
                destination = chatId,
                type = packetType,
                payload = payload,
                encrypted = false
            )
        val sent = MeshSocketClient.sendBlocking(route, packet)
        Log.d("GHALBIT-PING", "type=$packetType chatId=$chatId route=$route sent=$sent")
        PacketTraceStore.record(
            PacketTraceEntry(
                packetType = packetType,
                sourceNodeId = MainActivity.myGlobalPeerId,
                targetNodeId = chatId,
                routeType = decision.routeType.name,
                transport = decision.transport,
                deliveryState = if (sent) "TX_OK" else "TX_FAIL"
            )
        )
        if (sent) {
            updateState(
                chatId,
                config.globalId,
                lastPingAt = sentAt,
                latencyMs = states[chatId]?.latencyMs ?: -1L,
                route = route,
                health = when {
                    currentMisses >= 2 -> RouteHealthStatus.PROBING_ROUTE
                    currentMisses == 1 -> RouteHealthStatus.WEAK
                    else -> RouteHealthStatus.STABLE
                },
                packetLossEstimate = min(100, currentMisses * 20),
                transport = decision.transport
            )
            if (states[chatId]?.lastPongAt?.let { sentAt - it > PONG_TIMEOUT_MS } == true) {
                val newMisses = currentMisses + 1
                misses[chatId] = newMisses
                if (shouldDelayDemotion(context, config, route, newMisses)) {
                    Log.d("GHALBIT-LOW-SIGNAL", "holdRoute chatId=$chatId route=$route misses=$newMisses")
                    Log.d("GHALBIT-ROUTE-SCORE", "udp fresh")
                    Log.d("GHALBIT-ROUTE-SCORE", "tcp failed but udp alive")
                    Log.d("GHALBIT-ROUTE-SCORE", "demotion delayed")
                    updateState(
                        chatId = chatId,
                        globalId = config.globalId,
                        route = route,
                        health = RouteHealthStatus.PROBING_ROUTE,
                        packetLossEstimate = min(100, newMisses * 20),
                        transport = decision.transport
                    )
                    return
                }
                switchToFallback(context, config, "pongTimeout")
            }
        } else {
            val newMisses = currentMisses + 1
            misses[chatId] = newMisses
            if (shouldDelayDemotion(context, config, route, newMisses)) {
                updateState(
                    chatId = chatId,
                    globalId = config.globalId,
                    route = route,
                    health = RouteHealthStatus.PROBING_ROUTE,
                    packetLossEstimate = min(100, newMisses * 20),
                    transport = decision.transport
                )
                Log.d("GHALBIT-LOW-SIGNAL", "sendFailedHoldRoute chatId=$chatId route=$route misses=$newMisses")
                Log.d("GHALBIT-ROUTE-SCORE", "demotion delayed")
                return
            }
            switchToFallback(context, config, "sendFailed")
        }
    }

    private fun handlePing(context: Context, packet: MeshPacket, payload: String) {
        val sentAt = runCatching { JSONObject(payload).optLong("sentAt", System.currentTimeMillis()) }.getOrDefault(System.currentTimeMillis())
        val config = configs[packet.source] ?: SessionConfig(packet.source, CallManager.extractSourceGlobalId(payload), null, false)
        val route =
            IntelligentRouteMemory.getHint(context, packet.source)?.nextHopId
                ?: KeyStoreLike.peerAddress(context, packet.source)
        if (route.isNullOrBlank()) {
            Log.d("GHALBIT-ROUTE-HEALTH", "cannot reply ping source=${packet.source}")
            return
        }
        val pongPayload =
            JSONObject()
                .put("chatId", packet.source)
                .put("sourceNodeId", MainActivity.myGlobalPeerId)
                .put("sourceGlobalId", config.globalId)
                .put("sentAt", sentAt)
                .put("pongAt", System.currentTimeMillis())
                .toString()
        val pong =
            MeshPacket(
                packetId = "PONG-${System.currentTimeMillis()}",
                source = MainActivity.myGlobalPeerId,
                destination = packet.source,
                type = "PONG",
                payload = pongPayload,
                encrypted = false
            )
        val ok = MeshSocketClient.sendBlocking(route, pong)
        Log.d("GHALBIT-PONG", "reply source=${packet.source} route=$route sent=$ok")
        PacketTraceStore.record(
            PacketTraceEntry(
                packetType = "PONG",
                sourceNodeId = MainActivity.myGlobalPeerId,
                targetNodeId = packet.source,
                routeType = AdaptiveRouteType.LOCAL_MESH_DIRECT.name,
                transport = "LOCAL_MESH_DIRECT",
                deliveryState = if (ok) "TX_OK" else "TX_FAIL"
            )
        )
    }

    private fun handlePong(packet: MeshPacket, payload: String) {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return
        val chatId = json.optString("chatId").ifBlank { packet.source }
        val sentAt = json.optLong("sentAt", 0L)
        val pongAt = json.optLong("pongAt", System.currentTimeMillis())
        val latency = if (sentAt > 0L) (pongAt - sentAt).coerceAtLeast(0L) else -1L
        misses[chatId] = 0
        recordLatency(chatId, latency)
        val route = states[chatId]?.activeRoute ?: "-"
        updateState(
            chatId = chatId,
            globalId = configs[chatId]?.globalId,
            lastPongAt = pongAt,
            latencyMs = latency,
            route = route,
            health = if (latency in 0..1200L) RouteHealthStatus.STABLE else RouteHealthStatus.WEAK,
            packetLossEstimate = 0,
            transport = states[chatId]?.transport ?: "LOCAL_MESH_DIRECT"
        )
        Log.d("GHALBIT-PONG", "chatId=$chatId latency=$latency")
        Log.d("GHALBIT-LOW-SIGNAL", "recovered chatId=$chatId latency=$latency")
        PacketTraceStore.record(
            PacketTraceEntry(
                packetType = "PONG",
                sourceNodeId = packet.source,
                targetNodeId = chatId,
                routeType = states[chatId]?.routeHealth?.name ?: RouteHealthStatus.STABLE.name,
                transport = states[chatId]?.transport ?: "LOCAL_MESH_DIRECT",
                deliveryState = "RX_OK"
            )
        )
    }

    private fun handleRouteCheck(context: Context, packet: MeshPacket, payload: String) {
        Log.d("GHALBIT-ROUTE-HEALTH", "route-check source=${packet.source}")
        handlePing(context, packet, payload)
    }

    private fun switchToFallback(context: Context, config: SessionConfig, reason: String) {
        Log.d("GHALBIT-ROUTE-SWITCH", "chatId=${config.chatId} reason=$reason")
        if (reason == "pongTimeout" && states[config.chatId]?.transport?.contains("LOCAL_MESH") == true) {
            Log.w("GHALBIT-MESH-HEALTH", "stale direct hint")
            Log.w("GHALBIT-MESH-HEALTH", "route demoted")
            updateState(
                chatId = config.chatId,
                globalId = config.globalId,
                route = states[config.chatId]?.activeRoute ?: "-",
                health = RouteHealthStatus.ROUTE_DEMOTED_AFTER_CONFIRMATION,
                packetLossEstimate = min(100, (misses[config.chatId] ?: 0) * 20),
                transport = states[config.chatId]?.transport ?: "RECONNECTING"
            )
        }
        reconnectCounters[config.chatId] = (reconnectCounters[config.chatId] ?: 0) + 1
        updateState(
            chatId = config.chatId,
            globalId = config.globalId,
            route = states[config.chatId]?.activeRoute ?: "-",
            health = RouteHealthStatus.RECONNECTING,
            packetLossEstimate = min(100, (misses[config.chatId] ?: 0) * 20),
            transport = states[config.chatId]?.transport ?: "RECONNECTING"
        )

        val alternativeLocal =
            AdaptiveRouteManager.evaluate(
                context = context,
                chatId = config.chatId,
                globalId = config.globalId,
                routeHint = config.routeHint,
                keepAliveState = states[config.chatId]
            )
                .takeIf { it.routeType == AdaptiveRouteType.LOCAL_MESH_DIRECT || it.routeType == AdaptiveRouteType.LOCAL_RELAY || it.routeType == AdaptiveRouteType.NEARBY }
                ?.nextHop
                ?.takeIf { it != states[config.chatId]?.activeRoute }
        if (!alternativeLocal.isNullOrBlank()) {
            Log.d("GHALBIT-ROUTE-SWITCH", "chatId=${config.chatId} route=local alt=$alternativeLocal")
            configs[config.chatId] = config.copy(routeHint = alternativeLocal)
            updateState(config.chatId, config.globalId, route = alternativeLocal, health = RouteHealthStatus.WEAK, transport = "LOCAL_RELAY")
            return
        }

        val internetRoute = config.globalId?.let { OnlinePresenceManager.getOnlineRoute(context, it) }
        if (OnlinePresenceManager.hasInternet(context) && internetRoute != null) {
            scope.launch {
                val ok =
                    OnlineFallbackTransport.sendControlViaInternet(
                        internetRoute,
                        type = "ROUTE_CHECK",
                        payload = JSONObject().put("chatId", config.chatId).put("reason", reason).toString()
                    )
                if (ok) {
                    updateState(config.chatId, config.globalId, route = "internet:${internetRoute.relayUrl}", health = RouteHealthStatus.INTERNET_FALLBACK, transport = "INTERNET_RELAY")
                } else {
                    updateState(config.chatId, config.globalId, route = "internet-pending", health = RouteHealthStatus.OFFLINE_PENDING, transport = "PENDING_QUEUE")
                }
            }
            return
        }

        updateState(config.chatId, config.globalId, route = "pending", health = RouteHealthStatus.OFFLINE_PENDING, transport = "PENDING_QUEUE")
    }

    private fun updateState(
        chatId: String,
        globalId: String?,
        lastPingAt: Long = states[chatId]?.lastPingAt ?: 0L,
        lastPongAt: Long = states[chatId]?.lastPongAt ?: 0L,
        latencyMs: Long = states[chatId]?.latencyMs ?: -1L,
        rollingAverageLatencyMs: Long = averageLatency(chatId),
        route: String = states[chatId]?.activeRoute ?: "-",
        health: RouteHealthStatus = states[chatId]?.routeHealth ?: RouteHealthStatus.RECONNECTING,
        packetLossEstimate: Int = states[chatId]?.packetLossEstimate ?: 0,
        transport: String = states[chatId]?.transport ?: "-"
    ) {
        val reconnectCounter = reconnectCounters[chatId] ?: states[chatId]?.reconnectCounter ?: 0
        val effectiveHealth = RouteStateReconciler.reconcileHealth(chatId, globalId, health)
        val effectiveTransport = RouteStateReconciler.preferredTransport(chatId, globalId).takeIf { effectiveHealth != health } ?: transport
        val effectiveRoute = RouteStateReconciler.preferredRouteLabel(chatId, globalId).takeIf { effectiveHealth != health } ?: route
        val routeStabilityScore =
            (100 - packetLossEstimate - (rollingAverageLatencyMs.coerceAtLeast(0L) / 25L).toInt() - (reconnectCounter * 5))
                .coerceIn(0, 100)
        states[chatId] =
            ActiveConversationRouteState(
                chatId = chatId,
                globalId = globalId,
                lastPingAt = lastPingAt,
                lastPongAt = lastPongAt,
                latencyMs = latencyMs,
                rollingAverageLatencyMs = rollingAverageLatencyMs,
                packetLossEstimate = packetLossEstimate,
                routeStabilityScore = routeStabilityScore,
                reconnectCounter = reconnectCounter,
                transport = effectiveTransport,
                activeRoute = effectiveRoute,
                routeHealth = effectiveHealth
            )
        _stateFlow.value = states.toMap()
        Log.d("GHALBIT-ROUTE-HEALTH", "chatId=$chatId health=${effectiveHealth.label} route=$effectiveRoute loss=$packetLossEstimate latency=$latencyMs avg=$rollingAverageLatencyMs score=$routeStabilityScore reconnect=$reconnectCounter transport=$effectiveTransport")
        Log.d("GHALBIT-KEEPALIVE-HEALTH", "chatId=$chatId health=${effectiveHealth.name} avg=$rollingAverageLatencyMs loss=$packetLossEstimate score=$routeStabilityScore")
        Log.d("GHALBIT-LOW-SIGNAL", "score chatId=$chatId score=$routeStabilityScore health=${effectiveHealth.name} transport=$effectiveTransport")
        Log.d("GHALBIT-ROUTE-SCORE", "final score=$routeStabilityScore")
    }

    private fun shouldDelayDemotion(context: Context, config: SessionConfig, route: String?, failureCount: Int): Boolean {
        if (failureCount >= MAX_PROBING_FAILURES) return false
        if (route.isNullOrBlank()) return false
        val hint =
            config.globalId?.let { IntelligentRouteMemory.getHint(context, it) }
                ?: IntelligentRouteMemory.getHint(context, config.chatId)
        val freshHint = hint?.let { System.currentTimeMillis() - it.lastSeen <= FRESH_HINT_WINDOW_MS } == true
        return freshHint && (hint?.nextHopId == route || config.routeHint == route)
    }

    private fun sendInternetKeepAlive(config: SessionConfig, decision: AdaptiveRouteDecision) {
        val relayUrl = decision.nextHop ?: return
        scope.launch {
            val payload =
                JSONObject()
                    .put("chatId", config.chatId)
                    .put("sourceNodeId", MainActivity.myGlobalPeerId)
                    .put("sourceGlobalId", config.globalId)
                    .put("sentAt", System.currentTimeMillis())
                    .toString()
            val ok =
                OnlineFallbackTransport.sendControlViaInternet(
                    route = com.ghalbitnet.meshx2.online.InternetRoute(config.globalId ?: config.chatId, relayUrl),
                    type = "PING",
                    payload = payload
                )
            PacketTraceStore.record(
                PacketTraceEntry(
                    packetType = "PING",
                    sourceNodeId = MainActivity.myGlobalPeerId,
                    targetNodeId = config.chatId,
                    routeType = decision.routeType.name,
                    transport = decision.transport,
                    deliveryState = if (ok) "TX_OK" else "TX_FAIL"
                )
            )
            updateState(
                chatId = config.chatId,
                globalId = config.globalId,
                lastPingAt = System.currentTimeMillis(),
                route = "internet:$relayUrl",
                health = if (ok) RouteHealthStatus.INTERNET_FALLBACK else RouteHealthStatus.OFFLINE_PENDING,
                packetLossEstimate = if (ok) 0 else min(100, ((misses[config.chatId] ?: 0) + 1) * 20),
                transport = decision.transport
            )
        }
    }

    private fun nextKeepAliveDelay(chatId: String, config: SessionConfig?): Long {
        val state = states[chatId]
        val missCount = misses[chatId] ?: 0
        return when {
            config?.preferFastPing == true -> FAST_PING_MS
            missCount >= 3 || state?.routeHealth == RouteHealthStatus.RECONNECTING -> MINIMAL_SIGNAL_PING_MS
            missCount >= 1 || state?.routeHealth == RouteHealthStatus.WEAK || state?.routeHealth == RouteHealthStatus.PROBING_ROUTE -> WEAK_SIGNAL_PING_MS
            state?.routeHealth == RouteHealthStatus.STABLE && (state.routeStabilityScore >= 80) -> STABLE_LOW_POWER_PING_MS
            else -> NORMAL_PING_MS
        }
    }

    private fun recordLatency(chatId: String, latency: Long) {
        if (latency < 0L) return
        val history = latencyHistory.getOrPut(chatId) { ArrayDeque() }
        history.addLast(latency)
        while (history.size > 6) {
            history.removeFirst()
        }
    }

    private fun averageLatency(chatId: String): Long {
        val history = latencyHistory[chatId].orEmpty()
        if (history.isEmpty()) return states[chatId]?.rollingAverageLatencyMs ?: -1L
        return history.average().toLong()
    }

    private object KeyStoreLike {
        fun peerAddress(context: Context, chatId: String): String? {
            return com.ghalbitnet.meshx2.security.KeyStoreManager(context).getPeerAddress(chatId)
        }
    }
}
