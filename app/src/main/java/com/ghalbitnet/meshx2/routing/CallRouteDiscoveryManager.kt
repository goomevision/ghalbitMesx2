package com.ghalbitnet.meshx2.routing

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.call.CallManager
import com.ghalbitnet.meshx2.call.CallPeerEndpoint
import com.ghalbitnet.meshx2.core.node.NodeStatusManager
import com.ghalbitnet.meshx2.identity.IdentityServerClient
import com.ghalbitnet.meshx2.identity.IdentitySyncManager
import com.ghalbitnet.meshx2.online.OnlinePresenceManager
import com.ghalbitnet.meshx2.online.RelayConfigHealth
import com.ghalbitnet.meshx2.online.RelayConfigValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RouteDiscoveryResult(
    val state: RouteSearchState,
    val endpoint: CallPeerEndpoint? = null,
    val humanStatus: String,
    val selectedRouteType: String? = null
)

private data class RouteCandidate(
    val routeType: String,
    val endpoint: CallPeerEndpoint,
    val latencyMs: Long = 0L,
    val packetLoss: Int = 0,
    val routeStability: Int = 50,
    val hopCount: Int = 1,
    val batteryCost: Int = 10,
    val hasInternet: Boolean = false,
    val meshSignal: Int = 0,
    val lastSuccessfulBonus: Int = 0,
    val scoreAdjustment: Int = 0
)

object CallRouteDiscoveryManager {
    suspend fun discoverForCall(
        context: Context,
        peerName: String,
        ipHint: String? = null,
        globalIdHint: String? = null,
        publicKeyHint: String? = null,
        walletAddressHint: String? = null,
        displayNameHint: String? = null,
        emergencyPriority: Boolean = false,
        onStateChanged: ((RouteSearchState, String) -> Unit)? = null
    ): RouteDiscoveryResult = withContext(Dispatchers.IO) {
        val baseEndpoint =
            CallManager.resolvePeer(
                context = context,
                peerName = peerName,
                ipHint = ipHint,
                globalIdHint = globalIdHint,
                publicKeyHint = publicKeyHint,
                walletAddressHint = walletAddressHint,
                displayNameHint = displayNameHint
            )
        val identityKey = globalIdHint ?: baseEndpoint.globalId ?: peerName
        val routeKeys = RouteStateReconciler.keysFor(peerName, baseEndpoint.globalId ?: globalIdHint)
        val candidates = mutableListOf<RouteCandidate>()
        val aliveNodes = NodeStatusManager.getOnlineNodes().filter { it.online }
        val localPenalty = if (aliveNodes.isEmpty()) -30 else 0

        fun updateState(state: RouteSearchState, label: String, holdMs: Long = 8_000L) {
            RouteStateReconciler.mark(routeKeys, state, label, holdMs)
            onStateChanged?.invoke(state, label)
        }

        val relayValidation = RelayConfigValidator.validate(context, force = false)
        val relayHealth = RelayConfigHealth.from(relayValidation)
        val relayReady = relayHealth == RelayConfigHealth.READY && OnlinePresenceManager.hasInternet(context)

        updateState(RouteSearchState.SEARCHING_SERVER, "Mengecek server induk…")
        if (relayReady) {
            IdentityServerClient.lookupIdentity(context, identityKey)?.let { lookup ->
                val directHint = lookup.routeHint
                if (!directHint.isNullOrBlank()) {
                    candidates +=
                        RouteCandidate(
                            routeType = TriplePathRoutePolicy.SERVER_DIRECT_INTERNET,
                            endpoint = baseEndpoint.copy(
                                routeHint = directHint,
                                globalId = lookup.callId,
                                publicKey = lookup.publicKey,
                                displayName = lookup.displayName
                            ),
                            latencyMs = 40L,
                            routeStability = 85,
                            hasInternet = true,
                            lastSuccessfulBonus = 12
                        )
                }
                val relayHint = lookup.relayUrl ?: directHint?.takeIf { it.startsWith("http", true) }
                if (!relayHint.isNullOrBlank()) {
                    candidates +=
                        RouteCandidate(
                            routeType = TriplePathRoutePolicy.INTERNET_RELAY,
                            endpoint = baseEndpoint.copy(
                                routeHint = relayHint,
                                globalId = lookup.callId,
                                publicKey = lookup.publicKey,
                                displayName = lookup.displayName
                            ),
                            latencyMs = 65L,
                            routeStability = 78,
                            hasInternet = true,
                            lastSuccessfulBonus = 8,
                            scoreAdjustment = 6
                        )
                }
            }
        } else if (relayHealth == RelayConfigHealth.MISSING) {
            updateState(RouteSearchState.SEARCHING_LOCAL_MESH_PRIMARY, "Relay internet belum diatur")
        }

        updateState(RouteSearchState.SEARCHING_INTERNET_RELAY, "Mencari jalur internet…")
        if (relayReady) {
            baseEndpoint.globalId?.let { globalId ->
                OnlinePresenceManager.getOnlineRoute(context, globalId)?.let { route ->
                    candidates +=
                        RouteCandidate(
                            routeType = TriplePathRoutePolicy.INTERNET_RELAY,
                            endpoint = baseEndpoint.copy(routeHint = route.relayUrl, globalId = globalId),
                            latencyMs = 70L,
                            routeStability = 75,
                            hasInternet = true,
                            lastSuccessfulBonus = 10,
                            scoreAdjustment = 4
                        )
                }
            }
        }

        updateState(RouteSearchState.SEARCHING_LOCAL_MESH_PRIMARY, "Mencari node lokal terdekat…")
        val primaryHint = baseEndpoint.routeHint ?: baseEndpoint.transportIp
        if (!primaryHint.isNullOrBlank()) {
            val node = aliveNodes.firstOrNull { it.ipAddress == primaryHint }
            candidates +=
                RouteCandidate(
                    routeType = TriplePathRoutePolicy.LOCAL_MESH_PRIMARY,
                    endpoint = baseEndpoint.copy(routeHint = primaryHint, transportIp = primaryHint),
                    latencyMs = node?.latency?.toLong() ?: 25L,
                    routeStability = if (node?.online == true) 82 else 45,
                    hopCount = 1,
                    meshSignal = node?.signal ?: 55,
                    batteryCost = 6,
                    lastSuccessfulBonus = 8,
                    scoreAdjustment = localPenalty
                )
        }

        updateState(RouteSearchState.SEARCHING_LOCAL_MESH_SECONDARY, "Mencoba jalur mesh cadangan…")
        IntelligentRouteMemory.getHint(context, identityKey)?.let { hint ->
            candidates +=
                RouteCandidate(
                    routeType = TriplePathRoutePolicy.LOCAL_MESH_SECONDARY,
                    endpoint = baseEndpoint.copy(routeHint = hint.nextHopId, transportIp = hint.nextHopId),
                    latencyMs = hint.latencyMs,
                    routeStability = hint.trustScore,
                    hopCount = hint.hopCount,
                    meshSignal = 50,
                    batteryCost = 7,
                    lastSuccessfulBonus = 6,
                    scoreAdjustment = if (aliveNodes.isEmpty()) -18 else 0
                )
        }

        updateState(RouteSearchState.SEARCHING_COPY_NODES, "Mencari jejak copy identitas…")
        IdentitySyncManager.findBestCopyRouteHint(context, identityKey)?.let { copyHint ->
            candidates +=
                RouteCandidate(
                    routeType = TriplePathRoutePolicy.IDENTITY_COPY_TRACE,
                    endpoint = baseEndpoint.copy(routeHint = copyHint, transportIp = copyHint),
                    latencyMs = 95L,
                    routeStability = 58,
                    hopCount = 2,
                    meshSignal = 40,
                    batteryCost = 5,
                    lastSuccessfulBonus = 4,
                    scoreAdjustment = if (aliveNodes.isEmpty()) -14 else 0
                )
        }

        val ranked =
            candidates
                .map { candidate ->
                    candidate to RouteCandidateRanker.score(
                        routeType = candidate.routeType,
                        latencyMs = candidate.latencyMs,
                        packetLoss = candidate.packetLoss,
                        routeStability = candidate.routeStability,
                        hopCount = candidate.hopCount,
                        batteryCost = candidate.batteryCost,
                        hasInternet = candidate.hasInternet,
                        meshSignal = candidate.meshSignal,
                        lastSuccessfulBonus = candidate.lastSuccessfulBonus,
                        emergencyPriority = emergencyPriority,
                        scoreAdjustment = candidate.scoreAdjustment
                    )
                }
                .sortedByDescending { it.second.score }

        val best =
            ranked.firstNotNullOfOrNull { (candidate, score) ->
                if (!RouteProbeValidator.requiresProbe(candidate.routeType)) {
                    return@firstNotNullOfOrNull ValidatedRouteCandidate(
                        routeType = candidate.routeType,
                        endpoint = candidate.endpoint,
                        finalScore = score.score,
                        humanStatus = "Jalur ditemukan"
                    )
                }

                updateState(RouteSearchState.ROUTE_PROBING, "Menguji jalur terpilih…", holdMs = 4_000L)
                val probe = RouteProbeValidator.probe(context, candidate.routeType, candidate.endpoint)
                if (!probe.success) {
                    Log.d(
                        "GHALBIT-ROUTE-DISCOVERY",
                        "rejected staleHint=${probe.staleHint} routeType=${candidate.routeType} reason=${probe.reason}"
                    )
                    updateState(RouteSearchState.ROUTE_SWITCHING, "Mencoba jalur lain…", holdMs = 4_000L)
                    return@firstNotNullOfOrNull null
                }

                ValidatedRouteCandidate(
                    routeType = candidate.routeType,
                    endpoint = candidate.endpoint.copy(
                        routeHint = probe.host ?: candidate.endpoint.routeHint,
                        transportIp = probe.host ?: candidate.endpoint.transportIp
                    ),
                    finalScore = score.score + 15 - if (probe.staleHint) 18 else 0 - if (probe.aliveNodes == 0) 10 else 0,
                    humanStatus = if (probe.staleHint) "Mencoba jalur lain yang baru tervalidasi." else "Jalur ditemukan",
                    probeResult = probe,
                    staleHint = probe.staleHint
                )
            }

        if (best == null) {
            RouteStateReconciler.clear(routeKeys)
            Log.w("GHALBIT-ROUTE-DISCOVERY", "failed peer=$peerName")
            return@withContext RouteDiscoveryResult(
                state = RouteSearchState.FAILED,
                humanStatus = "Belum menemukan jalur. Pencarian tetap berjalan."
            )
        }

        updateState(RouteSearchState.ROUTE_SWITCHING, "Jalur ditemukan, menghubungkan…", holdMs = 3_500L)
        Log.d("GHALBIT-ROUTE-DISCOVERY", "selected=${best.routeType} score=${best.finalScore}")
        onStateChanged?.invoke(RouteSearchState.ROUTE_FOUND, "Jalur ditemukan")
        RouteDiscoveryResult(
            state = RouteSearchState.ROUTE_FOUND,
            endpoint = best.endpoint,
            humanStatus = best.humanStatus,
            selectedRouteType = best.routeType
        )
    }
}
