package com.ghalbitnet.meshx2.sos

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ghalbitnet.meshx2.MainActivity
import com.ghalbitnet.meshx2.activityfeed.ActivityFeedManager
import com.ghalbitnet.meshx2.activityfeed.ActivityFeedType
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.core.utils.AppNotificationManager
import com.ghalbitnet.meshx2.identity.CentralIdentityResolver
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.security.KeyStoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object SosAlertManager {
    const val ACTION_SOS_UPDATED = "com.ghalbitnet.meshx2.SOS_UPDATED"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _alerts = MutableStateFlow<List<SosAlert>>(emptyList())
    val alerts: StateFlow<List<SosAlert>> = _alerts

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        ActivityFeedManager.bind(appContext)
        _alerts.value = SosAlertStore.getAll(appContext)
        ActivityFeedManager.publish(
            type = ActivityFeedType.RUNTIME_EVENT,
            title = "SOS monitor aktif",
            message = "Sistem SOS siap merekam dan menampilkan peringatan darurat.",
            source = "SosAlertManager",
            metadata = "{\"alerts\":${_alerts.value.size}}"
        )
    }

    fun all(context: Context): List<SosAlert> {
        val alerts = SosAlertStore.getAll(context.applicationContext)
        _alerts.value = alerts
        return alerts
    }

    fun handleIncomingSos(
        context: Context,
        packet: MeshPacket,
        payload: String,
        routeHint: String?
    ): SosAlert? {
        val appContext = context.applicationContext
        ActivityFeedManager.bind(appContext)
        val keyStore = KeyStoreManager(appContext)
        val localGlobalId = GlobalMeshIdentityManager.buildGlobalId(keyStore.publicKeyBase64)
        val resolved =
            CentralIdentityResolver.resolve(
                context = appContext,
                legacyChatId = packet.source,
                peerName = packet.source,
                peerIp = routeHint,
                publicKeyHint = keyStore.getPeerKey(packet.source),
                displayNameHint = packet.source
            )

        val sourceGlobalId = resolved.globalId
        if (packet.source == MainActivity.myGlobalPeerId || (!sourceGlobalId.isNullOrBlank() && sourceGlobalId == localGlobalId)) {
            Log.d("GHALBIT-SOS-RX", "ignored self sos source=${packet.source}")
            ActivityFeedManager.publish(
                type = ActivityFeedType.RUNTIME_EVENT,
                title = "SOS sendiri diabaikan",
                message = "Paket SOS dari node lokal tidak dimasukkan ke inbox darurat.",
                peerId = packet.source,
                source = "SosAlertManager",
                metadata = "{\"alertId\":\"${packet.packetId}\",\"routeHint\":\"${routeHint ?: ""}\"}"
            )
            return null
        }

        val alert =
            SosAlert(
                alertId = packet.packetId,
                sourceNodeId = packet.source,
                sourceGlobalId = sourceGlobalId,
                receivedAt = System.currentTimeMillis(),
                message = payload,
                routeHint = routeHint,
                relayPath = routeHint
            )
        SosAlertStore.upsert(appContext, alert)
        _alerts.value = SosAlertStore.getAll(appContext)
        ActivityFeedManager.publish(
            type = ActivityFeedType.SOS_RECEIVED,
            title = "SOS diterima",
            message = "Peringatan darurat dari ${alert.sourceNodeId}: ${alert.message.take(120)}",
            peerId = alert.sourceNodeId,
            source = "SosAlertManager",
            metadata = "{\"alertId\":\"${alert.alertId}\",\"sourceGlobalId\":\"${alert.sourceGlobalId ?: ""}\",\"routeHint\":\"${alert.routeHint ?: ""}\"}"
        )
        Log.d("GHALBIT-SOS-RX", "alertId=${alert.alertId} node=${alert.sourceNodeId} globalId=${alert.sourceGlobalId ?: "-"} route=${alert.routeHint ?: "-"}")

        LocalBroadcastManager.getInstance(appContext).sendBroadcast(
            Intent(ACTION_SOS_UPDATED).apply {
                putExtra("alertId", alert.alertId)
                putExtra("sourceNodeId", alert.sourceNodeId)
                putExtra("sourceGlobalId", alert.sourceGlobalId)
                putExtra("message", alert.message)
                putExtra("routeHint", alert.routeHint)
            }
        )
        Log.d("GHALBIT-SOS-UI", "broadcast alertId=${alert.alertId}")

        AppNotificationManager.notifySos(
            context = appContext,
            peerName = alert.sourceNodeId,
            payload = "EMERGENCY | ${alert.sourceGlobalId ?: "-"} | ${alert.routeHint ?: "-"}",
            peerGlobalId = alert.sourceGlobalId,
            peerDisplayName = alert.sourceNodeId
        )
        ActivityFeedManager.publish(
            type = ActivityFeedType.SOS_RECEIVED,
            title = "Notifikasi SOS ditampilkan",
            message = "Peringatan dari ${alert.sourceNodeId} sudah dikirim ke notifikasi pengguna.",
            peerId = alert.sourceNodeId,
            source = "SosAlertManager",
            metadata = "{\"alertId\":\"${alert.alertId}\",\"notified\":true}"
        )
        Log.d("GHALBIT-SOS-NOTIFY", "notified alertId=${alert.alertId}")
        return alert
    }

    fun markRead(context: Context, alertId: String) {
        val appContext = context.applicationContext
        ActivityFeedManager.bind(appContext)
        SosAlertStore.markRead(appContext, alertId)
        _alerts.value = SosAlertStore.getAll(appContext)
        ActivityFeedManager.publish(
            type = ActivityFeedType.SOS_RECEIVED,
            title = "SOS dibaca",
            message = "Peringatan SOS $alertId ditandai sudah dibaca.",
            source = "SosAlertManager",
            metadata = "{\"alertId\":\"$alertId\",\"read\":true}"
        )
    }

    fun clearReadItems(context: Context): Int {
        val appContext = context.applicationContext
        ActivityFeedManager.bind(appContext)
        val removed = SosAlertStore.clearReadItems(appContext)
        _alerts.value = SosAlertStore.getAll(appContext)
        ActivityFeedManager.publish(
            type = ActivityFeedType.RUNTIME_EVENT,
            title = "Inbox SOS dibersihkan",
            message = "$removed peringatan SOS yang sudah dibaca dibersihkan dari inbox.",
            source = "SosAlertManager",
            metadata = "{\"removed\":$removed}"
        )
        return removed
    }

    fun postUi(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
