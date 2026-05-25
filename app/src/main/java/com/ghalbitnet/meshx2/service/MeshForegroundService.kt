package com.ghalbitnet.meshx2.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.MainActivity
import com.ghalbitnet.meshx2.core.runtime.MeshRuntimeState

class MeshForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "GHALBIT_MESH_CHANNEL"
    }

    private var lastMeshRunning: Boolean? = null
    private var lastNodeCount: Int = -1
    private var lastGatewaySummary: String = ""
    private var lastError: String = ""

    private val runtimeListener:
        (MeshRuntimeState.Snapshot) -> Unit = { snapshot ->
            if (shouldRefreshNotification(snapshot)) {
                NotificationController.update(
                    context = this,
                    key = "mesh",
                    notificationId = 1,
                    payload = buildNotificationPayload(snapshot),
                    reason = "MESH_RUNTIME_SIGNIFICANT"
                )
                rememberSnapshot(snapshot)
            }
        }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        val snapshot = MeshRuntimeState.snapshot()
        NotificationController.clear("mesh")
        NotificationController.startForeground(
            service = this,
            key = "mesh",
            notificationId = 1,
            payload = buildNotificationPayload(snapshot),
            reason = "MESH_SERVICE_START"
        )
        rememberSnapshot(snapshot)

        MeshRuntimeState.addListener(runtimeListener)
    }

    private fun buildNotificationPayload(
        snapshot: MeshRuntimeState.Snapshot
    ): NotificationController.NotificationPayload {
        val openAppIntent =
            Intent(
                this,
                MainActivity::class.java
            )

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        val contentText =
            when {
                snapshot.lastError.isNotBlank() ->
                    "Perlu perhatian: ${snapshot.lastError.take(40)}"

                snapshot.gatewaySummary.isNotBlank() && snapshot.nodeCount > 0 ->
                    "${snapshot.nodeCount} node aktif | ${snapshot.gatewaySummary.take(48)}"

                snapshot.nodeCount > 0 ->
                    "${snapshot.nodeCount} node aktif terhubung"

                snapshot.isMeshRunning ->
                    "Mesh aktif, menunggu node"

                else ->
                    "Mesh belum aktif"
            }

        val detailText =
            buildString {
                append("Status: ")
                append(if (snapshot.isMeshRunning) "ONLINE" else "OFFLINE")
                if (snapshot.gatewaySummary.isNotBlank()) {
                    append(" | Gateway: ")
                    append(snapshot.gatewaySummary)
                }
            }

        val notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("GHALBIT Mesh")
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$contentText\n$detailText")
            )
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

        return NotificationController.NotificationPayload(
            title = "GHALBIT Mesh",
            text = contentText,
            mode = if (snapshot.isMeshRunning) "MESH_RUNNING" else "MESH_STOPPED",
            connectionState = "${snapshot.isMeshRunning}|${snapshot.nodeCount}|${snapshot.gatewaySummary}|${snapshot.lastError}",
            notification = notification
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GHALBIT Mesh Service",
                NotificationManager.IMPORTANCE_LOW
            )

            channel.description =
                "Foreground service untuk mesh networking"

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        val snapshot = MeshRuntimeState.snapshot()
        NotificationController.startForeground(
            service = this,
            key = "mesh",
            notificationId = 1,
            payload = buildNotificationPayload(snapshot),
            reason = "MESH_SERVICE_RESTART"
        )
        rememberSnapshot(snapshot)

        return START_STICKY
    }

    override fun onDestroy() {
        MeshRuntimeState.removeListener(runtimeListener)
        NotificationController.clear("mesh")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun shouldRefreshNotification(snapshot: MeshRuntimeState.Snapshot): Boolean {
        if (lastMeshRunning == null) return true
        if (lastMeshRunning != snapshot.isMeshRunning) return true
        if (lastError != snapshot.lastError) return true
        if (lastGatewaySummary != snapshot.gatewaySummary) return true
        return kotlin.math.abs(snapshot.nodeCount - lastNodeCount) >= 2
    }

    private fun rememberSnapshot(snapshot: MeshRuntimeState.Snapshot) {
        lastMeshRunning = snapshot.isMeshRunning
        lastNodeCount = snapshot.nodeCount
        lastGatewaySummary = snapshot.gatewaySummary
        lastError = snapshot.lastError
    }
}
