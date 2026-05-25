package com.ghalbitnet.meshx2.vpn

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.ghalbitnet.meshx2.service.MeshVpnService

object VpnController {

    /**
     * Kontrak saat ini:
     * - controller hanya mengirim intent start/stop ke `MeshVpnService`
     * - controller tidak memverifikasi lifecycle service Android secara sinkron
     * - `markDesiredRunning()` berarti "diinginkan aktif/nonaktif", bukan jaminan service
     *   sudah benar-benar masuk state final
     *
     * TODO(core-stabilization):
     * Tambahkan read-model status final yang menyatukan desired state, persisted service
     * state, dan runtime snapshot agar UI/controller tidak memakai interpretasi berbeda.
     */
    fun prepareIntent(context: Context): Intent? {
        if (VpnOperatingMode.current(context) == VpnOperatingMode.MONITORING_PASSIVE) {
            return null
        }
        return VpnService.prepare(context)
    }

    fun start(context: Context): Result<Unit> {
        return runCatching {
            VpnLogManager.info(
                "VPN_CONTROLLER_START",
                "Memulai service dengan mode ${VpnOperatingMode.current(context).name}."
            )
            VpnRuntimeState.markDesiredRunning(true)
            ContextCompat.startForegroundService(
                context,
                Intent(context, MeshVpnService::class.java).apply {
                    action = MeshVpnService.ACTION_START_BRIDGE_MONITOR
                }
            )
        }
    }

    fun stop(context: Context): Result<Unit> {
        return runCatching {
            VpnLogManager.info("VPN_CONTROLLER_STOP", "Menghentikan service monitoring/VPN.")
            VpnRuntimeState.markDesiredRunning(false)
            context.startService(
                Intent(context, MeshVpnService::class.java).apply {
                    action = MeshVpnService.ACTION_STOP_BRIDGE_MONITOR
                }
            )
        }
    }

    fun scheduleReconnect(context: Context, delayMs: Long = 3_000L) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val restartIntent =
            PendingIntent.getService(
                context,
                77,
                Intent(context, MeshVpnService::class.java).apply {
                    action = MeshVpnService.ACTION_START_BRIDGE_MONITOR
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + delayMs,
            restartIntent
        )
    }
}
