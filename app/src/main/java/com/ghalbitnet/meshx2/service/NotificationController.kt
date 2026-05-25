package com.ghalbitnet.meshx2.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import com.ghalbitnet.meshx2.vpn.VpnLogManager
import java.util.concurrent.ConcurrentHashMap

object NotificationController {

    private const val MIN_UPDATE_INTERVAL_MS = 60_000L

    data class NotificationState(
        val lastTitle: String,
        val lastText: String,
        val lastMode: String,
        val lastConnectionState: String,
        val lastUpdatedAt: Long
    )

    data class NotificationPayload(
        val title: String,
        val text: String,
        val mode: String,
        val connectionState: String,
        val notification: Notification
    )

    private val stateCache = ConcurrentHashMap<String, NotificationState>()

    fun startForeground(
        service: Service,
        key: String,
        notificationId: Int,
        payload: NotificationPayload,
        reason: String
    ) {
        service.startForeground(notificationId, payload.notification)
        stateCache[key] =
            NotificationState(
                lastTitle = payload.title,
                lastText = payload.text,
                lastMode = payload.mode,
                lastConnectionState = payload.connectionState,
                lastUpdatedAt = System.currentTimeMillis()
            )
        VpnLogManager.info("NOTIFICATION_REAL_UPDATE", "key=$key reason=$reason via=startForeground")
    }

    fun update(
        context: Context,
        key: String,
        notificationId: Int,
        payload: NotificationPayload,
        reason: String,
        force: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val previous = stateCache[key]
        if (!force && previous != null) {
            val unchanged =
                previous.lastTitle == payload.title &&
                    previous.lastText == payload.text &&
                    previous.lastMode == payload.mode &&
                    previous.lastConnectionState == payload.connectionState
            if (unchanged) {
                VpnLogManager.info(
                    "NOTIFICATION_CONTENT_UNCHANGED",
                    "key=$key reason=$reason"
                )
                return
            }
            if (now - previous.lastUpdatedAt < MIN_UPDATE_INTERVAL_MS) {
                VpnLogManager.info(
                    "NOTIFICATION_UPDATE_BLOCKED_BY_INTERVAL",
                    "key=$key reason=$reason"
                )
                return
            }
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        runCatching {
            manager.notify(notificationId, payload.notification)
            stateCache[key] =
                NotificationState(
                    lastTitle = payload.title,
                    lastText = payload.text,
                    lastMode = payload.mode,
                    lastConnectionState = payload.connectionState,
                    lastUpdatedAt = now
                )
            VpnLogManager.info("NOTIFICATION_REAL_UPDATE", "key=$key reason=$reason via=notify")
        }.onFailure {
            VpnLogManager.error("VPN_NOTIFY", "Gagal memperbarui notifikasi terpusat.", it)
        }
    }

    fun clear(key: String) {
        stateCache.remove(key)
    }
}
