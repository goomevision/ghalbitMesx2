package com.ghalbitnet.meshx2.call

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.BuildConfig
import com.ghalbitnet.meshx2.chat.ConversationKeepAliveManager
import com.ghalbitnet.meshx2.chat.RouteHealthStatus
import com.ghalbitnet.meshx2.online.OnlineFallbackTransport
import com.ghalbitnet.meshx2.online.OnlinePresenceManager
import com.ghalbitnet.meshx2.online.RelayConfigValidation
import com.ghalbitnet.meshx2.online.RelayConfigValidator
import com.ghalbitnet.meshx2.routing.IntelligentRouteMemory

data class VoipActionResult(
    val routeType: VoipRouteType,
    val engineName: String,
    val statusMessage: String,
    val useLegacyAudio: Boolean,
    val fallbackToPtt: Boolean,
    val videoAllowed: Boolean = false
)

object GhalbitCallManager {
    private val webRtcAdapter = WebRtcVoipEngineAdapter()
    private val linphoneAdapter = LinphoneVoipEngineAdapter()
    @Suppress("unused")
    private val sipAdapter = SipVoipEngineAdapter()

    fun initialize(context: Context) {
        linphoneAdapter.initialize(context)
        linphoneAdapter.setConnectionStateListener { Log.d("GHALBIT-VOIP", "engine=${linphoneAdapter.engineName} state=$it") }
        linphoneAdapter.setAudioQualityListener { Log.d("GHALBIT-VOIP-AUDIO", "engine=${linphoneAdapter.engineName} quality=$it") }
        webRtcAdapter.initialize(context)
    }

    fun preferredEngineName(): String = linphoneAdapter.engineName

    fun isSdkReady(context: Context): Boolean {
        initialize(context)
        return linphoneAdapter.isInitialized()
    }

    fun localNodeId(): String = com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager.localNodeId()

    fun localGlobalId(): String = com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager.localGlobalId()

    fun localPublicKeyHash(): String = com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager.localPublicKeyHash()

    fun localIdentityReady(): Boolean =
        localNodeId().isNotBlank() && localGlobalId().isNotBlank() && localPublicKeyHash().isNotBlank()

    fun resolveRoute(context: Context, peer: CallPeerEndpoint): VoipRouteType {
        val routeMode = GhalbitRouteMode.fromRaw(BuildConfig.GHALBIT_ROUTE_MODE)
        val routeScore = evaluateNearbyRouteScore(context, peer)
        when (routeMode) {
            GhalbitRouteMode.FORCE_RELAY_ONLY -> {
                Log.d("GHALBIT-ROUTE-MODE", "force relay only")
                val relayReady = RelayConfigValidator.cached(context).state == RelayConfigValidation.State.INTERNET_RELAY_READY
                if (!relayReady) {
                    Log.w("GHALBIT-ROUTE-MODE", "relay blocked reason=missingConfig")
                    return VoipRouteType.FALLBACK_PTT
                }
                return if (!peer.globalId.isNullOrBlank() && OnlinePresenceManager.getOnlineRoute(context, peer.globalId) != null) {
                    VoipRouteType.INTERNET_RELAY
                } else {
                    VoipRouteType.FALLBACK_PTT
                }
            }
            GhalbitRouteMode.FORCE_MESH_ONLY -> {
                Log.d("GHALBIT-ROUTE-MODE", "force mesh only")
                return if (routeScore.nearbyDetected) VoipRouteType.NEARBY else if (!peer.routeHint.isNullOrBlank() || !peer.transportIp.isNullOrBlank()) VoipRouteType.LOCAL_MESH else VoipRouteType.FALLBACK_PTT
            }
            GhalbitRouteMode.AUTO_HYBRID -> Log.d("GHALBIT-ROUTE-MODE", "auto hybrid")
        }

        val localCandidate = !peer.routeHint.isNullOrBlank() || !peer.transportIp.isNullOrBlank()
        if (localCandidate && routeScore.nearbyDetected) {
            Log.d("GHALBIT-ROUTE-SCORE", "nearby detected")
            return VoipRouteType.NEARBY
        }
        if (localCandidate && routeScore.voiceProbeReady) {
            Log.d("GHALBIT-ROUTE-SCORE", "voice probe ok")
            return VoipRouteType.LOCAL_MESH
        }
        if (localCandidate && shouldDemoteLocalMesh(peer)) {
            Log.w("GHALBIT-MESH-HEALTH", "stale direct hint")
            Log.w("GHALBIT-MESH-HEALTH", "route demoted")
        } else if (localCandidate) {
            return VoipRouteType.LOCAL_MESH
        }
        if (!peer.globalId.isNullOrBlank() && OnlinePresenceManager.getOnlineRoute(context, peer.globalId) != null) {
            return if (RelayConfigValidator.cached(context).state == RelayConfigValidation.State.INTERNET_RELAY_READY) {
                VoipRouteType.INTERNET_RELAY
            } else {
                VoipRouteType.FALLBACK_PTT
            }
        }
        return VoipRouteType.FALLBACK_PTT
    }

    fun evaluateNearbyRouteScore(context: Context, peer: CallPeerEndpoint): NearbyRouteScore {
        val route = peer.routeHint ?: peer.transportIp
        val hint =
            peer.globalId?.let { IntelligentRouteMemory.getHint(context, it) }
                ?: IntelligentRouteMemory.getHint(context, peer.nodeId)
        val keepAliveState =
            ConversationKeepAliveManager.snapshot(peer.nodeId)
                ?: peer.globalId?.let { ConversationKeepAliveManager.snapshot(it) }
        val now = System.currentTimeMillis()
        val udpFresh = hint?.let { now - it.lastSeen <= 10_000L } == true
        val verified = !peer.globalId.isNullOrBlank() && !peer.publicKeyHash.isNullOrBlank()
        val sameIp = !route.isNullOrBlank() && route == hint?.nextHopId
        val recentSuccess = keepAliveState?.lastPongAt?.let { now - it <= 12_000L } == true
        val latency = keepAliveState?.rollingAverageLatencyMs?.takeIf { it >= 0L } ?: hint?.latencyMs ?: 0L
        val loss = keepAliveState?.packetLossEstimate ?: 0
        val jitterPenalty = (latency / 25L).toInt().coerceAtMost(25)
        val score =
            (40 +
                if (udpFresh) 20 else 0 +
                if (verified) 15 else 0 +
                if (sameIp) 10 else 0 +
                if (recentSuccess) 15 else 0 -
                jitterPenalty -
                (loss / 5))
                .coerceIn(0, 100)
        if (udpFresh) {
            Log.d("GHALBIT-ROUTE-SCORE", "udp fresh")
        }
        if (udpFresh && loss >= 100) {
            Log.d("GHALBIT-ROUTE-SCORE", "tcp failed but udp alive")
        }
        val nearbyDetected = !route.isNullOrBlank() && (udpFresh || recentSuccess || sameIp)
        val delayedDemotion = nearbyDetected && loss >= 100 && !ConversationKeepAliveManager.isRouteStaleAfterConfirmation(peer.nodeId, route)
        if (delayedDemotion) {
            Log.d("GHALBIT-ROUTE-SCORE", "demotion delayed")
        }
        val status =
            when {
                score >= 70 && nearbyDetected -> RouteHealthStatus.LOCAL_VOICE_READY
                nearbyDetected && delayedDemotion -> RouteHealthStatus.PROBING_ROUTE
                nearbyDetected -> RouteHealthStatus.NEARBY_DETECTED
                loss >= 100 -> RouteHealthStatus.ROUTE_DEMOTED_AFTER_CONFIRMATION
                else -> RouteHealthStatus.LOCAL_VOICE_UNSTABLE
            }
        Log.d("GHALBIT-ROUTE-SCORE", "final score=$score")
        return NearbyRouteScore(
            status = status,
            score = score,
            nearbyDetected = nearbyDetected,
            voiceProbeReady = score >= 70 && nearbyDetected,
            shouldDelayDemotion = delayedDemotion,
            routeSummary = "route=${route ?: "-"} loss=$loss latency=$latency verified=$verified"
        )
    }

    suspend fun dispatchSignalEvent(context: Context, event: CallSignalEvent): CallSignalDispatchResult {
        val peer =
            CallPeerEndpoint(
                nodeId = event.nodeId,
                globalId = event.globalId,
                publicKey = event.publicKey,
                publicKeyHash = event.publicKeyHash,
                walletAddress = event.walletAddress,
                displayName = event.displayName,
                routeHint = event.routeHint,
                transportIp = event.transportIp
            )
        val routeType = resolveRoute(context, peer)
        val payload =
            CallManager.buildSignalPayload(
                callId = event.callId,
                localNodeId = event.localNodeId,
                localGlobalId = event.localGlobalId,
                localPublicKeyHash = event.localPublicKeyHash
            )
        return when (routeType) {
            VoipRouteType.INTERNET_RELAY -> {
                val route = event.globalId?.let { OnlinePresenceManager.getOnlineRoute(context, it) }
                if (route != null && OnlineFallbackTransport.sendCallSignalViaInternet(context, route, event.type, payload)) {
                    Log.d("GHALBIT-CALL-SIGNAL", "sent relay type=${event.type}")
                    CallSignalDispatchResult(true, "ACCEPT_SENT_RELAY", "Menghubungkan lewat relay.", routeType)
                } else {
                    Log.d("GHALBIT-CALL-SIGNAL", "pending no route type=${event.type}")
                    CallSignalDispatchResult(false, "ACCEPT_QUEUED", "Menunggu jalur internet.", routeType)
                }
            }
            VoipRouteType.LOCAL_MESH,
            VoipRouteType.LOCAL_RELAY,
            VoipRouteType.NEARBY -> {
                val sent =
                    CallManager.sendSignal(
                        context = context,
                        peer = peer,
                        type = event.type,
                        callId = event.callId,
                        localNodeId = event.localNodeId,
                        localGlobalId = event.localGlobalId,
                        localPublicKeyHash = event.localPublicKeyHash
                    )
                if (sent) {
                    Log.d("GHALBIT-CALL-SIGNAL", "sent mesh type=${event.type}")
                    CallSignalDispatchResult(true, "ACCEPT_SENT_MESH", "Menghubungkan lewat mesh.", routeType)
                } else {
                    Log.w("GHALBIT-MESH-HEALTH", "udp seen but tcp failed")
                    Log.w("GHALBIT-MESH-HEALTH", "route demoted")
                    CallSignalDispatchResult(false, "ACCEPT_QUEUED", "Menunggu jalur mesh sehat.", routeType)
                }
            }
            VoipRouteType.FALLBACK_PTT -> {
                Log.d("GHALBIT-CALL-SIGNAL", "pending no route type=${event.type}")
                CallSignalDispatchResult(false, "ACCEPT_QUEUED", "Menunggu jalur tersedia.", routeType)
            }
        }
    }

    fun videoCapability(context: Context, peer: CallPeerEndpoint): MediaCapabilityReport =
        MediaCapabilityChecker.evaluate(context, resolveRoute(context, peer))

    suspend fun startOutgoingCall(
        context: Context,
        callId: String,
        peer: CallPeerEndpoint,
        localNodeId: String,
        localGlobalId: String?,
        localPublicKeyHash: String?
    ): VoipActionResult {
        initialize(context)
        val routeType = resolveRoute(context, peer)
        val target = toTarget(callId, peer, routeType, false)
        val signaling = buildSignalingChannel(context, peer, routeType, localNodeId, localGlobalId, localPublicKeyHash)
        logRoute(target, routeType)
        if (routeType == VoipRouteType.FALLBACK_PTT || signaling == null) {
            Log.w("GHALBIT-VOIP-FALLBACK", "target=${target.globalId ?: target.nodeId} fallback=PTT")
            return VoipActionResult(routeType, "LEGACY_PTT", "Realtime tidak siap. Gunakan push-to-talk.", true, true)
        }
        val sdkStarted = linphoneAdapter.startAudioCall(context, target, signaling)
        return if (sdkStarted) {
            VoipActionResult(routeType, linphoneAdapter.engineName, routeMessage(routeType), true, false)
        } else {
            Log.w("GHALBIT-VOIP-FALLBACK", "target=${target.globalId ?: target.nodeId} engine=${linphoneAdapter.engineName} fallback=PTT")
            VoipActionResult(routeType, "LEGACY_AUDIO", "SDK belum bisa menjaga media. Menggunakan audio fallback.", true, false)
        }
    }

    suspend fun acceptIncomingCall(
        context: Context,
        callId: String,
        peer: CallPeerEndpoint,
        localNodeId: String,
        localGlobalId: String?,
        localPublicKeyHash: String?
    ): VoipActionResult {
        initialize(context)
        val routeType = resolveRoute(context, peer)
        val target = toTarget(callId, peer, routeType, true)
        val signaling = buildSignalingChannel(context, peer, routeType, localNodeId, localGlobalId, localPublicKeyHash)
        logRoute(target, routeType)
        if (signaling == null) {
            return VoipActionResult(VoipRouteType.FALLBACK_PTT, "LEGACY_PTT", "Jalur realtime belum tersedia.", true, true)
        }
        val accepted = linphoneAdapter.acceptAudioCall(context, target, signaling)
        return if (accepted) {
            VoipActionResult(routeType, linphoneAdapter.engineName, routeMessage(routeType), true, false)
        } else {
            signaling.sendCallAccept(target, CallManager.buildSignalPayload(callId, localNodeId, localGlobalId, localPublicKeyHash))
            VoipActionResult(routeType, "LEGACY_AUDIO", "Menggunakan audio fallback.", true, false)
        }
    }

    suspend fun endCall(
        context: Context,
        callId: String,
        peer: CallPeerEndpoint,
        localNodeId: String,
        localGlobalId: String?,
        localPublicKeyHash: String?
    ): Boolean {
        val routeType = resolveRoute(context, peer)
        val target = toTarget(callId, peer, routeType, false)
        val signaling = buildSignalingChannel(context, peer, routeType, localNodeId, localGlobalId, localPublicKeyHash) ?: return false
        return linphoneAdapter.endCall(context, target, signaling)
    }

    suspend fun startVideoCall(
        context: Context,
        callId: String,
        peer: CallPeerEndpoint,
        localNodeId: String,
        localGlobalId: String?,
        localPublicKeyHash: String?
    ): VoipActionResult {
        initialize(context)
        val routeType = resolveRoute(context, peer)
        val target = toTarget(callId, peer, routeType, false)
        val capability = videoCapability(context, peer)
        if (!capability.videoRecommended) {
            return VoipActionResult(routeType, linphoneAdapter.engineName, capability.reason, true, false, false)
        }
        val signaling = buildSignalingChannel(context, peer, routeType, localNodeId, localGlobalId, localPublicKeyHash)
        if (signaling == null) {
            return VoipActionResult(VoipRouteType.FALLBACK_PTT, linphoneAdapter.engineName, "Video tidak tersedia pada jalur ini.", true, true, false)
        }
        val started = linphoneAdapter.startVideoCall(context, target, signaling)
        return if (started) {
            VoipActionResult(routeType, linphoneAdapter.engineName, "Video ringan diminta melalui ${routeMessage(routeType).lowercase()}", true, false, true)
        } else {
            VoipActionResult(routeType, linphoneAdapter.engineName, "Video tidak tersedia pada perangkat/jalur ini.", true, false, false)
        }
    }

    fun stopVideo() {
        linphoneAdapter.stopVideo()
    }

    fun muteMic(muted: Boolean) {
        linphoneAdapter.muteMic(muted)
    }

    fun setSpeaker(enabled: Boolean) {
        linphoneAdapter.setSpeaker(enabled)
    }

    private fun toTarget(callId: String, peer: CallPeerEndpoint, routeType: VoipRouteType, incoming: Boolean) =
        VoipTarget(callId, peer.nodeId, peer.globalId, peer.publicKeyHash, peer.displayName ?: peer.nodeId, routeType, peer.routeHint ?: peer.transportIp, incoming)

    private fun buildSignalingChannel(
        context: Context,
        peer: CallPeerEndpoint,
        routeType: VoipRouteType,
        localNodeId: String,
        localGlobalId: String?,
        localPublicKeyHash: String?
    ): CallSignalingChannel? {
        return when (routeType) {
            VoipRouteType.INTERNET_RELAY -> {
                val route = peer.globalId?.let { OnlinePresenceManager.getOnlineRoute(context, it) } ?: return null
                InternetRelaySignalingChannel(context, route)
            }
            VoipRouteType.LOCAL_MESH,
            VoipRouteType.LOCAL_RELAY,
            VoipRouteType.NEARBY -> LocalMeshSignalingChannel(context, peer, localNodeId, localGlobalId, localPublicKeyHash)
            VoipRouteType.FALLBACK_PTT -> null
        }
    }

    private fun routeMessage(routeType: VoipRouteType): String =
        when (routeType) {
            VoipRouteType.LOCAL_MESH -> "jalur lokal"
            VoipRouteType.LOCAL_RELAY -> "relay lokal"
            VoipRouteType.NEARBY -> "jalur nearby"
            VoipRouteType.INTERNET_RELAY -> "jalur internet"
            VoipRouteType.FALLBACK_PTT -> "fallback push-to-talk"
        }

    private fun shouldDemoteLocalMesh(peer: CallPeerEndpoint): Boolean {
        val hint = peer.routeHint ?: peer.transportIp ?: return false
        return ConversationKeepAliveManager.isRouteStaleAfterConfirmation(peer.nodeId, hint) ||
            peer.globalId?.let { ConversationKeepAliveManager.isRouteStaleAfterConfirmation(it, hint) } == true
    }

    private fun logRoute(target: VoipTarget, routeType: VoipRouteType) {
        Log.d("GHALBIT-VOIP-ROUTE", "target=${target.globalId ?: target.nodeId} route=$routeType")
    }
}
