package com.ghalbitnet.meshx2.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.ghalbitnet.meshx2.chat.LiveContactSync
import com.ghalbitnet.meshx2.online.OnlinePresenceManager
import com.ghalbitnet.meshx2.routing.IntelligentRouteMemory

data class VoipReadinessReport(
    val sdkReady: Boolean,
    val engineName: String,
    val audioPermissionGranted: Boolean,
    val audioRouteReady: Boolean,
    val localIdentityReady: Boolean,
    val routeAvailable: Boolean,
    val fallbackPttAvailable: Boolean,
    val videoAllowed: Boolean,
    val peerSummary: String,
    val routeSummary: String,
    val failureReason: String?
) {
    fun summary(): String =
        "sdk=$sdkReady engine=$engineName mic=$audioPermissionGranted audioRoute=$audioRouteReady identity=$localIdentityReady route=$routeAvailable fallback=$fallbackPttAvailable video=$videoAllowed peer=$peerSummary reason=${failureReason ?: "-"}"
}

data class VoipDryRunReport(
    val engineName: String,
    val localMeshResult: String,
    val internetRelayResult: String,
    val fallbackResult: String,
    val chosenRoute: String,
    val reason: String
) {
    fun summary(): String =
        "engine=$engineName chosen=$chosenRoute local=$localMeshResult internet=$internetRelayResult fallback=$fallbackResult reason=$reason"
}

object VoipReadinessChecker {
    @Volatile
    var lastReadinessReport: VoipReadinessReport? = null
        private set

    @Volatile
    var lastDryRunReport: VoipDryRunReport? = null
        private set

    fun check(context: Context): VoipReadinessReport {
        val appContext = context.applicationContext
        GhalbitCallManager.initialize(appContext)
        val contacts = LiveContactSync.build(appContext)
        val livePeer = contacts.firstOrNull { it.isLive }
        val onlinePeer = contacts.firstOrNull { !it.globalId.isNullOrBlank() && OnlinePresenceManager.getOnlineRoute(appContext, it.globalId) != null }
        val localIdentityReady =
            GhalbitCallManager.localIdentityReady() &&
                GhalbitCallManager.localNodeId().isNotBlank() &&
                GhalbitCallManager.localGlobalId().isNotBlank()
        val audioPermissionGranted =
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val audioRouteReady = audioManager != null
        val routeAvailable =
            livePeer != null ||
                onlinePeer != null ||
                IntelligentRouteMemory.getAllHints(appContext).isNotEmpty()
        val peerSummary =
            livePeer?.let { "${it.chatId} local" }
                ?: onlinePeer?.let { "${it.chatId} internet" }
                ?: "Belum ada peer aktif"
        val routeSummary =
            when {
                livePeer != null -> "LOCAL_MESH"
                onlinePeer != null -> "INTERNET_RELAY"
                else -> "FALLBACK_PTT"
            }
        val peerEndpoint =
            livePeer?.let {
                CallPeerEndpoint(
                    nodeId = it.chatId,
                    globalId = it.globalId,
                    publicKey = null,
                    publicKeyHash = it.publicKeyHash,
                    walletAddress = it.walletAddress,
                    displayName = it.displayName,
                    routeHint = it.routeHint,
                    transportIp = it.routeHint
                )
            }
        val videoAllowed = peerEndpoint?.let { GhalbitCallManager.videoCapability(appContext, it).videoRecommended } ?: false
        val failureReason =
            when {
                !localIdentityReady -> "Identity lokal belum lengkap"
                !audioPermissionGranted -> "Izin microphone belum diberikan"
                !audioRouteReady -> "Audio route belum siap"
                !routeAvailable -> "Belum ada peer aktif"
                !GhalbitCallManager.isSdkReady(appContext) -> "SDK VoIP belum siap"
                else -> null
            }
        return VoipReadinessReport(
            sdkReady = GhalbitCallManager.isSdkReady(appContext),
            engineName = GhalbitCallManager.preferredEngineName(),
            audioPermissionGranted = audioPermissionGranted,
            audioRouteReady = audioRouteReady,
            localIdentityReady = localIdentityReady,
            routeAvailable = routeAvailable,
            fallbackPttAvailable = true,
            videoAllowed = videoAllowed,
            peerSummary = peerSummary,
            routeSummary = routeSummary,
            failureReason = failureReason
        ).also {
            lastReadinessReport = it
            Log.d("GHALBIT-VOIP-READY", it.summary())
        }
    }

    fun dryRun(context: Context): VoipDryRunReport {
        val readiness = check(context)
        val localResult =
            if (readiness.routeSummary == "LOCAL_MESH") "READY" else "WAITING_PEER"
        val internetResult =
            if (readiness.routeSummary == "INTERNET_RELAY") "READY" else "NOT_SELECTED"
        val fallbackResult =
            if (readiness.fallbackPttAvailable) "AVAILABLE" else "UNAVAILABLE"
        val chosenRoute =
            when {
                readiness.routeSummary == "LOCAL_MESH" -> "LOCAL_MESH"
                readiness.routeSummary == "INTERNET_RELAY" -> "INTERNET_RELAY"
                else -> "FALLBACK_PTT"
            }
        val reason =
            when (chosenRoute) {
                "LOCAL_MESH" -> "Peer lokal tersedia"
                "INTERNET_RELAY" -> "Peer lokal tidak ada, internet relay tersedia"
                else -> readiness.failureReason ?: "Fallback tetap tersedia"
            }
        return VoipDryRunReport(
            engineName = readiness.engineName,
            localMeshResult = localResult,
            internetRelayResult = internetResult,
            fallbackResult = fallbackResult,
            chosenRoute = chosenRoute,
            reason = reason
        ).also {
            lastDryRunReport = it
            Log.d("GHALBIT-VOIP-DRYRUN", it.summary())
            Log.d("GHALBIT-VOIP-CHECK", "peer=${readiness.peerSummary} route=${readiness.routeSummary}")
            if (chosenRoute == "FALLBACK_PTT") {
                Log.w("GHALBIT-VOIP-FALLBACK", "dryrun fallback=PTT reason=$reason")
            }
        }
    }
}
