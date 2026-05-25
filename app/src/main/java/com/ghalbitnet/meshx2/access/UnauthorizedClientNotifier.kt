package com.ghalbitnet.meshx2.access

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.settings.UnauthorizedClientsActivity
import com.ghalbitnet.meshx2.vpn.VpnLogManager

object UnauthorizedClientNotifier {

    private const val CHANNEL_ID = "GHALBIT_UNAUTHORIZED_CLIENTS"
    private const val PREFS_NAME = "ghalbit_unauthorized_client_notifier"
    private const val ALERT_DEBOUNCE_MS = 5 * 60 * 1000L

    fun showIfNew(
        context: Context,
        ipAddress: String,
        macAddress: String?
    ) {
        val key = macAddress?.takeIf { it.isNotBlank() } ?: ipAddress
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastShownAt = prefs.getLong(key, 0L)
        if (now - lastShownAt < ALERT_DEBOUNCE_MS) {
            VpnLogManager.info(
                "UNAUTHORIZED_CLIENT_ALERT_SUPPRESSED",
                "client=$ipAddress mac=${macAddress ?: "-"}"
            )
            return
        }
        prefs.edit().putLong(key, now).apply()
        ensureChannel(context)
        val intent =
            Intent(context, UnauthorizedClientsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(UnauthorizedClientsActivity.EXTRA_OPENED_FROM_NOTIFICATION, true)
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                ("unauth:$key").hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Perangkat tidak diizinkan terdeteksi")
                .setContentText("IP $ipAddress tersambung tanpa izin Ghalbit")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "IP $ipAddress tersambung tanpa izin Ghalbit. Buka daftar pengguna tidak diizinkan untuk menandai BLOKIR atau IZINKAN."
                    )
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        NotificationManagerCompat.from(context).notify(("unauth:$key").hashCode(), notification)
        VpnLogManager.info(
            "UNAUTHORIZED_CLIENT_ALERT_SHOWN",
            "client=$ipAddress mac=${macAddress ?: "-"}"
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Peringatan Pengguna Tidak Diizinkan",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Memberi tahu provider saat ada perangkat hotspot tanpa izin Ghalbit."
            }
        manager.createNotificationChannel(channel)
    }
}
