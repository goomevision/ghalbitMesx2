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

// ================================
// IMPORT R PROJECT
// WAJIB sesuai package aplikasi
// ================================
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.MainActivity

class MeshForegroundService : Service() {

    companion object {

        // ==========================================
        // CHANNEL UNTUK NOTIFICATION FOREGROUND
        // ==========================================
        private const val CHANNEL_ID = "GHALBIT_MESH_CHANNEL"

        // ==========================================
        // MASA DEPAN:
        // Tambahkan channel lain:
        //
        // private const val CHAT_CHANNEL
        // private const val FILE_CHANNEL
        // private const val BLOCKCHAIN_CHANNEL
        // ==========================================
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        startForeground(
            1,
            buildNotification()
        )
    }

    // ======================================================
    // MEMBUAT NOTIFICATION
    // ======================================================
    private fun buildNotification(): Notification {
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

        return NotificationCompat.Builder(this, CHANNEL_ID)

            // ==================================================
            // GUNAKAN DRAWABLE AGAR TIDAK ERROR MIPMAP
            // ==================================================
            .setSmallIcon(R.drawable.ic_launcher_foreground)

            .setContentTitle("GHALBIT Mesh")
            .setContentText("Mesh network aktif")
            .setContentIntent(pendingIntent)

            .setOngoing(true)

            .setPriority(NotificationCompat.PRIORITY_LOW)

            // ==================================================
            // MASA DEPAN:
            //
            // .addAction(...)
            // tombol SOS
            // tombol disconnect
            // status node
            // status blockchain
            // status VPN
            //
            // .setStyle(...)
            // expanded notification
            //
            // .setSilent(true)
            //
            // ==================================================

            .build()
    }

    // ======================================================
    // CHANNEL ANDROID 8+
    // ======================================================
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
        startForeground(
            1,
            buildNotification()
        )

        // ==========================================
        // MASA DEPAN:
        //
        // Handle action:
        // START_MESH
        // STOP_MESH
        // RESTART_VPN
        // SYNC_BLOCKCHAIN
        // EMERGENCY_MODE
        //
        // ==========================================

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        // ==========================================
        // MASA DEPAN:
        //
        // cleanup VPN
        // cleanup sockets
        // stop discovery
        // save node state
        //
        // ==========================================
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
