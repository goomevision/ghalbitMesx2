package com.ghalbitnet.meshx2.dashboard

import android.content.Context
import android.text.format.DateFormat
import android.util.Log
import com.ghalbitnet.meshx2.call.CallManager
import com.ghalbitnet.meshx2.call.VoipReadinessChecker
import com.ghalbitnet.meshx2.chat.AdaptiveRouteManager
import com.ghalbitnet.meshx2.call.VoiceCallRegistry
import com.ghalbitnet.meshx2.chat.ConversationKeepAliveManager
import com.ghalbitnet.meshx2.chat.ChatDeliveryManager
import com.ghalbitnet.meshx2.chat.LiveContactSync
import com.ghalbitnet.meshx2.chat.PeerVerificationStatus
import com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager
import com.ghalbitnet.meshx2.core.runtime.MultiNodeValidationManager
import com.ghalbitnet.meshx2.core.runtime.PacketTraceStore
import com.ghalbitnet.meshx2.online.PendingMessageStore
import com.ghalbitnet.meshx2.online.RelayRealtimeChannel
import com.ghalbitnet.meshx2.routing.IntelligentRouteMemory
import com.ghalbitnet.meshx2.sos.SosAlertManager
import java.util.Date
import java.util.Locale

object RuntimeDashboardProvider {
    fun snapshot(context: Context): RuntimeDashboardSnapshot {
        val appContext = context.applicationContext
        val contacts = MeshRuntimeManager.contactRoster.value.ifEmpty { LiveContactSync.build(appContext) }
        val routes = IntelligentRouteMemory.getAllHints(appContext)
        val alerts = SosAlertManager.all(appContext)
        val latestAlert = alerts.maxByOrNull { it.receivedAt }
        val activeTransports = MeshRuntimeManager.activeTransports()
        val callSession = VoiceCallRegistry.activeSession
        val aliveNodes = MeshRuntimeManager.aliveNodes.value
        val activeRoutes = AdaptiveRouteManager.activeRoutes()
        val keepAliveStates = ConversationKeepAliveManager.stateFlow.value.values.toList()
        val readinessSummary = VoipReadinessChecker.lastReadinessReport?.summary() ?: "Belum diuji"
        val dryRunSummary = VoipReadinessChecker.lastDryRunReport?.summary() ?: "Belum diuji"
        val chatDryRunSummary = ChatDeliveryManager.lastDryRun.value.summary
        val pendingItems = PendingMessageStore.all(appContext)
        val mediaPendingItems = pendingItems.filter { !it.mediaUri.isNullOrBlank() }
        val nextMediaRetry = mediaPendingItems.map { it.nextRetryAt }.filter { it > 0L }.minOrNull()
        val validationSummary =
            listOf(
                MultiNodeValidationManager.verifyPeerVisibility(appContext),
                MultiNodeValidationManager.verifyHeartbeatConsistency(),
                MultiNodeValidationManager.verifyContactRosterConsistency(appContext),
                MultiNodeValidationManager.verifyRouteConsistency(appContext),
                MultiNodeValidationManager.verifyKeepAliveConsistency()
            )
        val snapshot =
            RuntimeDashboardSnapshot(
                isRunning = MeshRuntimeManager.isRunning,
                startedAt = MeshRuntimeManager.startedAt,
                uptimeLabel = formatDuration(MeshRuntimeManager.runtimeUptimeMs()),
                lastRestartReason = MeshRuntimeManager.lastRestartReason,
                localNodeId = MeshRuntimeManager.localNodeId(),
                localGlobalId = MeshRuntimeManager.localGlobalId(),
                localPublicKeyHash = MeshRuntimeManager.localPublicKeyHash(),
                activeTransports = activeTransports,
                heartbeatAliveNodes = aliveNodes,
                udpListenerStatus = statusOf(activeTransports, "UDP Listener"),
                socketServerStatus = statusOf(activeTransports, "Socket Server"),
                nearbyStatus = statusOf(activeTransports, "Nearby"),
                wifiDirectStatus = statusOf(activeTransports, "WiFi Direct"),
                totalContacts = contacts.size,
                liveContacts = contacts.count { it.isLive },
                offlineContacts = contacts.count { !it.isLive },
                savedContacts = contacts.count { it.isSaved },
                provisionalPeers = contacts.count { it.verificationStatus == PeerVerificationStatus.PROVISIONAL },
                peerVerificationSummary =
                    "verified=${contacts.count { it.verificationStatus == PeerVerificationStatus.VERIFIED }} provisional=${contacts.count { it.verificationStatus == PeerVerificationStatus.PROVISIONAL }} stale=${contacts.count { it.verificationStatus == PeerVerificationStatus.STALE }}",
                knownRoutes = routes.size,
                bestRouteHints = routes.sortedByDescending { it.trustScore }.take(3).map {
                    "${it.destinationId} -> ${it.nextHopId}"
                },
                lastRouteUpdate = MeshRuntimeManager.lastRouteUpdate.ifBlank { "Belum ada update route" },
                activeRoutes = activeRoutes.map { "${it.chatId}:${it.routeType.name}@${it.nextHop ?: "-"}" },
                currentTransport = activeRoutes.firstOrNull()?.transport ?: "IDLE",
                routeSwitchHistory = AdaptiveRouteManager.switchHistory(),
                totalSosAlerts = alerts.size,
                unreadSosAlerts = alerts.count { !it.isRead },
                lastSosSource = latestAlert?.let { "${it.sourceNodeId} / ${it.sourceGlobalId ?: "-"}" } ?: "Belum ada SOS",
                lastSosTime = latestAlert?.receivedAt?.let { formatTime(appContext, it) } ?: "-",
                callState = VoiceCallRegistry.activeState.name,
                remotePeer = callSession?.remoteNodeId ?: VoiceCallRegistry.activePeerName ?: "-",
                audioEngineStatus = if (VoiceCallRegistry.activeState.name == "CONNECTED") "ACTIVE" else "IDLE",
                audioTxCount = CallManager.audioTxCount(),
                audioRxCount = CallManager.audioRxCount(),
                keepAliveHealth =
                    keepAliveStates.joinToString(
                        separator = " | ",
                        transform = {
                        "${it.chatId}:${it.routeHealth.name}/score=${it.routeStabilityScore}"
                    }).ifBlank { "Tidak ada keepalive aktif" },
                voipReadinessSummary = readinessSummary,
                voipDryRunSummary = dryRunSummary,
                chatDeliverySummary = chatDryRunSummary,
                chatPendingSummary =
                    if (pendingItems.isEmpty()) {
                        "Tidak ada pending queue"
                    } else {
                        "pending=${pendingItems.size} media=${mediaPendingItems.size} realtime=${RelayRealtimeChannel.isConnected()} nextMediaRetry=${nextMediaRetry ?: 0L} last=${pendingItems.maxByOrNull { it.createdAt }?.messageId ?: "-"}"
                    },
                pendingQueueCount = pendingItems.size,
                recentPacketTrace = PacketTraceStore.recentLines(),
                validationSummary = validationSummary,
                lastWarning = MeshRuntimeManager.lastWarning.ifBlank { "Tidak ada warning" },
                lastError = MeshRuntimeManager.lastErrorSummary.ifBlank { "Tidak ada error" },
                lastPacket = MeshRuntimeManager.lastPacketSummary.ifBlank { "Belum ada packet tercatat" },
                runtimeStatus = MeshRuntimeManager.runtimeStatus.value
            )
        Log.d(
            "GHALBIT-DASHBOARD-SNAPSHOT",
            "runtime=${snapshot.isRunning} alive=${snapshot.heartbeatAliveNodes} contactsLive=${snapshot.liveContacts} sosUnread=${snapshot.unreadSosAlerts} routes=${snapshot.knownRoutes}"
        )
        return snapshot
    }

    private fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "0s"
        val totalSeconds = durationMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%dh %02dm %02ds", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%dm %02ds", minutes, seconds)
        }
    }

    private fun formatTime(context: Context, time: Long): String {
        return DateFormat.format("dd/MM HH:mm:ss", Date(time)).toString()
    }

    private fun statusOf(activeTransports: List<String>, name: String): String {
        return if (activeTransports.contains(name)) "ACTIVE" else "IDLE"
    }
}
