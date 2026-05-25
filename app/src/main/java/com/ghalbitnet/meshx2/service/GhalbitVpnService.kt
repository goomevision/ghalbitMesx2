package com.ghalbitnet.meshx2.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.ghalbitnet.meshx2.MainActivity
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.economy.InternetBridgeStateManager
import com.ghalbitnet.meshx2.security.KeyStoreManager
import com.ghalbitnet.meshx2.vpn.AccessPolicyManager
import com.ghalbitnet.meshx2.vpn.PacketDecisionEngine
import com.ghalbitnet.meshx2.vpn.PacketRouter
import com.ghalbitnet.meshx2.vpn.VpnController
import com.ghalbitnet.meshx2.vpn.VpnLogManager
import com.ghalbitnet.meshx2.vpn.VpnOperatingMode
import com.ghalbitnet.meshx2.vpn.UsageMeter
import com.ghalbitnet.meshx2.vpn.UsageRepository
import com.ghalbitnet.meshx2.vpn.VpnRuntimeState
import com.ghalbitnet.meshx2.vpn.VpnTunWriter
import com.ghalbitnet.meshx2.vpn.UsageDownloadMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.io.FileInputStream

/**
 * Implementasi runtime utama untuk VPN / monitoring service.
 *
 * Tanggung jawab aktual saat ini:
 * - lifecycle foreground service
 * - mode `MONITORING_PASSIVE` / `MONITORING_LIGHT`
 * - pembuatan TUN saat mode non-passive
 * - packet loop dan runtime guard
 * - integrasi usage meter, TrafficStats download monitor, dan notification controller
 *
 * Catatan audit:
 * - kelas ini belum terdaftar langsung di manifest
 * - caller aplikasi saat ini menargetkan [MeshVpnService]
 * - artinya [MeshVpnService] berperan sebagai façade publik, sementara kelas ini
 *   adalah basis implementasi runtime
 *
 * TODO(core-stabilization):
 * Pisahkan dengan lebih tegas antara:
 * 1. service publik yang stabil untuk manifest/caller
 * 2. implementasi runtime VPN internal
 * 3. orchestration policy/controller di luar service
 */
open class GhalbitVpnService : VpnService() {

    companion object {
        private const val CHANNEL_ID = "GHALBIT_VPN_RUNTIME"
        private const val NOTIFICATION_ID = 2126
        private const val PREFS_NAME = "ghalbit_vpn_runtime"
        private const val KEY_ACTIVE = "active"
        private const val KEY_DESIRED = "desired"

        const val ACTION_START_VPN = "com.ghalbitnet.meshx2.action.START_VPN"
        const val ACTION_STOP_VPN = "com.ghalbitnet.meshx2.action.STOP_VPN"

        fun isVpnActive(context: Context): Boolean {
            return prefs(context).getBoolean(KEY_ACTIVE, false)
        }

        private fun setVpnActive(context: Context, active: Boolean) {
            prefs(context).edit().putBoolean(KEY_ACTIVE, active).apply()
        }

        private fun setDesired(context: Context, desired: Boolean) {
            prefs(context).edit().putBoolean(KEY_DESIRED, desired).apply()
        }

        private fun isDesired(context: Context): Boolean {
            return prefs(context).getBoolean(KEY_DESIRED, false)
        }

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var tunInterface: ParcelFileDescriptor? = null
    private var tunOutputStream: FileOutputStream? = null
    private var packetJob: Job? = null
    private var runtimeJob: Job? = null
    private var manualStopRequested: Boolean = false
    private var activeGatewayId: String = ""
    private var activeGatewayName: String = ""
    private var activeUsageSessionId: String = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_VPN -> startVpnInternal()
            ACTION_STOP_VPN -> stopVpnInternal("VPN_STOPPED_MANUAL")
            else -> startVpnInternal()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        packetJob?.cancel()
        runtimeJob?.cancel()
        UsageMeter.closeActiveSession()
        if (activeUsageSessionId.isNotBlank()) {
            UsageRepository.closeSession(activeUsageSessionId)
        }
        UsageDownloadMonitor.reset(this)
        NotificationController.clear("vpn")
        VpnTunWriter.detach()
        runCatching { tunOutputStream?.close() }
        tunOutputStream = null
        tunInterface?.closeQuietly()
        tunInterface = null
        activeUsageSessionId = ""
        setVpnActive(this, false)
        VpnRuntimeState.markServiceStatus(
            VpnRuntimeState.ServiceStatus.VPN_STOPPED,
            "VPN service dihentikan."
        )
        if (!manualStopRequested && isDesired(this)) {
            VpnLogManager.warn("VPN_RECONNECT", "Service mati, menjadwalkan reconnect otomatis.")
            VpnController.scheduleReconnect(this)
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!manualStopRequested && isDesired(this)) {
            VpnLogManager.warn("VPN_TASK_REMOVED", "Android menutup task, reconnect dijadwalkan.")
            VpnController.scheduleReconnect(this, 1_500L)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onRevoke() {
        VpnLogManager.warn("VPN_REVOKED", "Izin VPN dicabut oleh Android.")
        stopVpnInternal("VPN_REVOKED")
        super.onRevoke()
    }

    private fun startVpnInternal() {
        val requestedMode = VpnOperatingMode.current(this)
        if (tunInterface != null || (requestedMode == VpnOperatingMode.MONITORING_PASSIVE && isVpnActive(this))) {
            VpnLogManager.info("VPN_ALREADY_ACTIVE", "VPN sudah aktif, permintaan start diabaikan.")
            return
        }

        manualStopRequested = false
        setDesired(this, true)
        if (requestedMode == VpnOperatingMode.ENFORCEMENT) {
            VpnOperatingMode.set(this, VpnOperatingMode.MONITORING_PASSIVE)
        }
        VpnRuntimeState.markDesiredRunning(true)
        VpnRuntimeState.resetCounters()
        val mode = VpnOperatingMode.current(this)
        val localGlobalId =
            GlobalMeshIdentityManager.buildGlobalId(KeyStoreManager(this).publicKeyBase64)
        activeUsageSessionId = "vpn-$localGlobalId-${System.currentTimeMillis()}"
        UsageRepository.init(this)
        UsageRepository.setOperatingMode(mode.name)
        UsageRepository.startSession(
            nodeId = localGlobalId,
            mode = mode.name,
            sessionId = activeUsageSessionId
        )
        if (mode == VpnOperatingMode.MONITORING_PASSIVE) {
            VpnLogManager.info("VPN_MONITORING_PASSIVE_ACTIVE", "Monitoring pasif aktif tanpa TUN.")
        } else if (mode == VpnOperatingMode.MONITORING_LIGHT) {
            VpnLogManager.info("VPN_MONITORING_LIGHT_ACTIVE", "VPN berjalan dalam mode monitoring ringan.")
        }
        createNotificationChannel()
        NotificationController.clear("vpn")
        NotificationController.startForeground(
            service = this,
            key = "vpn",
            notificationId = NOTIFICATION_ID,
            payload = buildNotificationPayload(),
            reason = "SERVICE_START_${mode.name}"
        )
        if (mode == VpnOperatingMode.MONITORING_PASSIVE) {
            UsageDownloadMonitor.startSession(this)
            setVpnActive(this, true)
            activeGatewayId = ""
            activeGatewayName = ""
            VpnRuntimeState.markActiveGatewayName(null)
            VpnRuntimeState.markServiceStatus(
                VpnRuntimeState.ServiceStatus.VPN_ACTIVE,
                "Monitoring pasif aktif tanpa TUN."
            )
            InternetBridgeStateManager.mark(
                this,
                InternetBridgeStateManager.BridgeState.ACTIVE,
                "Monitoring pasif aktif. Trafik tidak diintersep."
            )
            startRuntimeGuard()
            VpnLogManager.info(
                "PASSIVE_USAGE_POLL_STARTED",
                "Polling usage pasif dimulai tiap 1 detik tanpa TUN."
            )
            return
        }
        VpnLogManager.info("VPN_STARTING", "Memulai VpnService dan menyiapkan TUN interface.")

        val builder =
            Builder()
                .setSession("GhalbitMesh X2")
                .setMtu(1500)
                .addAddress("10.77.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")

        val established =
            runCatching { builder.establish() }
                .onFailure {
                    VpnLogManager.error("VPN_ESTABLISH_FAILED", "Gagal membuat TUN interface.", it)
                }
                .getOrNull()

        if (established == null) {
            VpnRuntimeState.markServiceStatus(
                VpnRuntimeState.ServiceStatus.VPN_STOPPED,
                "VPN gagal aktif. Periksa izin Android."
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        tunInterface = established
        tunOutputStream = FileOutputStream(established.fileDescriptor)
        tunOutputStream?.let { VpnTunWriter.attach(it) }
        UsageDownloadMonitor.startSession(this)
        setVpnActive(this, true)
        activeGatewayId = ""
        activeGatewayName = ""
        VpnRuntimeState.markActiveGatewayName(null)
        VpnRuntimeState.markServiceStatus(
            VpnRuntimeState.ServiceStatus.VPN_ACTIVE,
            "VPN aktif dan TUN interface berhasil dibuat."
        )
        InternetBridgeStateManager.mark(
            this,
            InternetBridgeStateManager.BridgeState.ACTIVE,
            "VPN aktif. Semua paket internet masuk ke decision engine Ghalbit."
        )
        VpnLogManager.info("VPN_ACTIVE", "TUN interface aktif.")

        startPacketLoop(established)
        startRuntimeGuard()
        NotificationController.update(
            context = this,
            key = "vpn",
            notificationId = NOTIFICATION_ID,
            payload = buildNotificationPayload(),
            reason = "VPN_TUN_STARTED",
            force = true
        )
    }

    private fun stopVpnInternal(reason: String) {
        manualStopRequested = true
        setDesired(this, false)
        VpnRuntimeState.markDesiredRunning(false)
        packetJob?.cancel()
        runtimeJob?.cancel()
        UsageMeter.closeActiveSession()
        if (activeUsageSessionId.isNotBlank()) {
            UsageRepository.closeSession(activeUsageSessionId)
        }
        UsageDownloadMonitor.reset(this)
        NotificationController.clear("vpn")
        VpnTunWriter.detach()
        runCatching { tunOutputStream?.close() }
        tunOutputStream = null
        tunInterface?.closeQuietly()
        tunInterface = null
        activeGatewayId = ""
        activeGatewayName = ""
        VpnRuntimeState.markActiveGatewayName(null)
        activeUsageSessionId = ""
        setVpnActive(this, false)
        VpnRuntimeState.markServiceStatus(
            VpnRuntimeState.ServiceStatus.VPN_STOPPED,
            reason
        )
        InternetBridgeStateManager.mark(
            this,
            InternetBridgeStateManager.BridgeState.WAITING,
            reason
        )
        VpnLogManager.info("VPN_STOPPED", reason)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startPacketLoop(descriptor: ParcelFileDescriptor) {
        packetJob?.cancel()
        packetJob =
            serviceScope.launch {
                val input = FileInputStream(descriptor.fileDescriptor)
                val buffer = ByteArray(32767)
                val localGlobalId =
                    GlobalMeshIdentityManager.buildGlobalId(KeyStoreManager(this@GhalbitVpnService).publicKeyBase64)

                while (isActive) {
                    try {
                        val length = input.read(buffer)
                        if (length <= 0) {
                            delay(10)
                            continue
                        }

                        val packet = buffer.copyOf(length)
                        VpnRuntimeState.recordPacketIn()
                        val mode = VpnOperatingMode.current(this@GhalbitVpnService)
                        val decision =
                            PacketDecisionEngine.decide(this@GhalbitVpnService, localGlobalId, packet)

                        if (decision.accessDecision.gatewayAvailable) {
                            VpnRuntimeState.markGatewayStatus(
                                VpnRuntimeState.GatewayStatus.GATEWAY_AVAILABLE,
                                decision.accessDecision.gatewayName.ifBlank { "Gateway siap" }
                            )
                        } else {
                            VpnRuntimeState.markGatewayStatus(
                                VpnRuntimeState.GatewayStatus.GATEWAY_NOT_AVAILABLE,
                                decision.accessDecision.detail
                            )
                        }

                        if (mode == VpnOperatingMode.MONITORING_LIGHT) {
                            VpnRuntimeState.recordAllowed("MONITORING_LIGHT")
                            PacketRouter.forwardPacket(this@GhalbitVpnService, packet, decision)
                            VpnRuntimeState.recordForwardedOut()
                            continue
                        }

                        when (decision.action) {
                            PacketDecisionEngine.Action.ALLOW_PACKET -> {
                                VpnRuntimeState.recordAllowed(decision.detail)
                                PacketRouter.forwardPacket(this@GhalbitVpnService, packet, decision)
                                VpnRuntimeState.recordForwardedOut()
                            }
                            PacketDecisionEngine.Action.DROP_PACKET -> {
                                VpnRuntimeState.recordDropped(decision.detail)
                                PacketRouter.dropPacket(this@GhalbitVpnService, packet, decision)
                            }
                        }
                    } catch (error: Throwable) {
                        if (!isActive) break
                        VpnLogManager.error("VPN_PACKET_LOOP", "Loop pembacaan paket terganggu.", error)
                        delay(100)
                    }
                }
            }
    }

    private fun startRuntimeGuard() {
        runtimeJob?.cancel()
        runtimeJob =
            serviceScope.launch {
                val localGlobalId =
                    GlobalMeshIdentityManager.buildGlobalId(KeyStoreManager(this@GhalbitVpnService).publicKeyBase64)
                while (isActive) {
                    val operatingMode = VpnOperatingMode.current(this@GhalbitVpnService)
                    val sessionId =
                        activeUsageSessionId.ifBlank { "vpn-$localGlobalId-${System.currentTimeMillis()}" }
                    UsageDownloadMonitor.poll(
                        context = this@GhalbitVpnService,
                        nodeId = localGlobalId,
                        sessionId = sessionId
                    )
                    if (operatingMode == VpnOperatingMode.MONITORING_PASSIVE) {
                        VpnRuntimeState.markActiveGatewayName(null)
                        VpnRuntimeState.markGatewayStatus(
                            VpnRuntimeState.GatewayStatus.GATEWAY_NOT_AVAILABLE,
                            "MONITORING_PASSIVE | gateway dan mesh forwarding dimatikan."
                        )
                        VpnRuntimeState.markAccessStatus(
                            VpnRuntimeState.AccessStatus.ACCESS_ALLOWED,
                            "MONITORING_PASSIVE | usage dihitung dari TrafficStats tanpa TUN."
                        )
                        delay(1_000L)
                        continue
                    }
                    if (operatingMode == VpnOperatingMode.MONITORING_LIGHT) {
                        VpnRuntimeState.markActiveGatewayName(null)
                        VpnRuntimeState.markGatewayStatus(
                            VpnRuntimeState.GatewayStatus.GATEWAY_NOT_AVAILABLE,
                            "MONITORING_LIGHT | gateway check dimatikan sementara."
                        )
                        VpnRuntimeState.markAccessStatus(
                            VpnRuntimeState.AccessStatus.ACCESS_ALLOWED,
                            "MONITORING_LIGHT | paket dihitung ringan tanpa enforcement."
                        )
                        delay(1_000L)
                        continue
                    }

                    val access = AccessPolicyManager.evaluate(this@GhalbitVpnService, localGlobalId)
                    val gatewayAvailable = access.gatewayAvailable && access.gatewayName.isNotBlank()
                    if (gatewayAvailable) {
                        if (activeGatewayId.isNotBlank() && activeGatewayId != access.gatewayId) {
                            VpnLogManager.warn(
                                "VPN_FAILOVER",
                                "Gateway berpindah dari $activeGatewayName ke ${access.gatewayName}."
                            )
                        }
                        activeGatewayId = access.gatewayId
                        activeGatewayName = access.gatewayName
                        VpnRuntimeState.markActiveGatewayName(activeGatewayName)
                    } else if (activeGatewayId.isNotBlank()) {
                        VpnLogManager.warn(
                            "VPN_GATEWAY_LOST",
                            "Gateway $activeGatewayName tidak lagi tersedia."
                        )
                        activeGatewayId = ""
                        activeGatewayName = ""
                        VpnRuntimeState.markActiveGatewayName(null)
                    } else {
                        VpnRuntimeState.markActiveGatewayName(null)
                    }

                    if (operatingMode == VpnOperatingMode.MONITORING_LIGHT) {
                        VpnRuntimeState.markAccessStatus(
                            VpnRuntimeState.AccessStatus.ACCESS_ALLOWED,
                            "MONITORING_LIGHT | paket dihitung ringan tanpa enforcement."
                        )
                    } else if (operatingMode == VpnOperatingMode.MONITORING_ONLY) {
                        VpnRuntimeState.markAccessStatus(
                            VpnRuntimeState.AccessStatus.ACCESS_ALLOWED,
                            "MONITORING_ONLY | paket dihitung tanpa enforcement."
                        )
                    } else if (access.allowed) {
                        VpnRuntimeState.markAccessStatus(
                            VpnRuntimeState.AccessStatus.ACCESS_ALLOWED,
                            access.detail
                        )
                        if (!PacketRouter.isForwarderReady()) {
                            VpnLogManager.warn(
                                "VPN_REWARD_DEFERRED",
                                "Akses diizinkan, tetapi reward/mining belum sah karena forwarder belum siap."
                            )
                        }
                    } else {
                        VpnRuntimeState.markAccessStatus(
                            VpnRuntimeState.AccessStatus.ACCESS_BLOCKED,
                            access.detail
                        )
                    }
                    delay(1_000L)
                }
            }
    }

    private fun buildNotification(): Notification {
        return buildNotificationPayload().notification
    }

    private fun buildNotificationPayload(): NotificationController.NotificationPayload {
        val snapshot = VpnRuntimeState.snapshot()
        val openIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        val stopIntent =
            PendingIntent.getService(
                this,
                1,
                Intent(this, MeshVpnService::class.java).apply {
                    action = ACTION_STOP_VPN
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.ghalbit_vpn_notification_title))
            .setContentText(snapshot.lastDecision)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    getString(
                        R.string.ghalbit_vpn_notification_body,
                        snapshot.serviceStatus.name,
                        snapshot.gatewayStatus.name,
                        snapshot.accessStatus.name,
                        snapshot.packetInCount,
                        snapshot.packetAllowedCount,
                        snapshot.packetDroppedCount,
                        snapshot.lastDecision
                    )
                )
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.ghalbit_vpn_stop), stopIntent)
            .build()

        return NotificationController.NotificationPayload(
            title = getString(R.string.ghalbit_vpn_notification_title),
            text = snapshot.lastDecision,
            mode = VpnOperatingMode.current(this).name,
            connectionState = "${snapshot.serviceStatus.name}|${snapshot.gatewayStatus.name}|${snapshot.accessStatus.name}",
            notification = notification
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.ghalbit_vpn_channel_title),
                    NotificationManager.IMPORTANCE_LOW
                )
            channel.description = getString(R.string.ghalbit_vpn_channel_desc)
            manager.createNotificationChannel(channel)
        }
    }

    private fun ParcelFileDescriptor.closeQuietly() {
        runCatching { close() }
    }
}
