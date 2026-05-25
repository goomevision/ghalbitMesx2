package com.ghalbitnet.meshx2.vpn

import android.content.Context
import com.ghalbitnet.meshx2.economy.InternetBridgeStateManager
import com.ghalbitnet.meshx2.service.MeshVpnService

/**
 * Read-only provider untuk menyatukan pembacaan status VPN dari beberapa sumber.
 *
 * Provider ini sengaja tidak mengubah runtime service. Tugasnya hanya merakit:
 * - desired state
 * - persisted active flag
 * - runtime in-memory snapshot
 * - bridge state tingkat produk
 */
object VpnStatusProvider {

    fun snapshot(context: Context): VpnStatusSnapshot {
        val runtime = VpnRuntimeState.snapshot()
        val bridgeState = InternetBridgeStateManager.snapshot(context)
        val serviceActive = MeshVpnService.isBridgeServiceActive(context)
        val desiredRunning = runtime.desiredRunning
        val runtimeAvailable = runtime.updatedAt > 0L
        val runtimeFreshness = freshnessOf(runtime.runtimeAgeMs)
        val mode = VpnOperatingMode.current(context).name
        val uiStatus =
            when {
                !desiredRunning && !serviceActive -> "OFF"
                desiredRunning && !serviceActive -> "STARTING"
                serviceActive && runtimeAvailable -> "ACTIVE"
                serviceActive && !runtimeAvailable -> "ACTIVE_LIMITED"
                !desiredRunning && serviceActive -> "STOPPING"
                else -> "UNKNOWN"
            }

        val warning = buildWarning(serviceActive, desiredRunning, runtimeAvailable, bridgeState, runtime)

        return VpnStatusSnapshot(
            desiredRunning = desiredRunning,
            serviceActive = serviceActive,
            runtimeAvailable = runtimeAvailable,
            runtimeAgeMs = if (runtimeAvailable) runtime.runtimeAgeMs else null,
            runtimeFreshness = runtimeFreshness,
            mode = mode,
            gatewayName = runtime.activeGatewayName,
            connectedUsers = null,
            packetsIn = if (runtimeAvailable) runtime.packetInCount else null,
            packetsOut = if (runtimeAvailable) runtime.packetsForwardedOut else null,
            lastDecision = if (runtimeAvailable) runtime.lastDecision else bridgeState.detail,
            lastUpdatedAt = maxOf(runtime.updatedAt, bridgeState.updatedAt),
            uiStatus = uiStatus,
            warning = warning
        )
    }

    private fun buildWarning(
        serviceActive: Boolean,
        desiredRunning: Boolean,
        runtimeAvailable: Boolean,
        bridgeState: InternetBridgeStateManager.Snapshot,
        runtime: VpnRuntimeState.Snapshot
    ): String? {
        if (serviceActive && !runtimeAvailable) {
            return "Service aktif tetapi detail runtime belum tersedia."
        }
        if (serviceActive && freshnessOf(runtime.runtimeAgeMs) == RuntimeFreshness.STALE) {
            return "VPN runtime snapshot stale"
        }
        if (desiredRunning != serviceActive && runtime.updatedAt > 0L) {
            if (runtime.runtimeAgeMs > 15_000L) {
                return "Desired state dan service active flag belum sinkron selama lebih dari 15 detik."
            }
        }
        if (serviceActive && bridgeState.state != InternetBridgeStateManager.BridgeState.ACTIVE) {
            return "Bridge UI state belum mencerminkan service aktif."
        }
        if (!serviceActive && bridgeState.state == InternetBridgeStateManager.BridgeState.ACTIVE) {
            return "Bridge UI state masih ACTIVE padahal service flag nonaktif."
        }
        return null
    }

    private fun freshnessOf(runtimeAgeMs: Long): RuntimeFreshness {
        return when {
            runtimeAgeMs == Long.MAX_VALUE -> RuntimeFreshness.UNKNOWN
            runtimeAgeMs <= 5_000L -> RuntimeFreshness.FRESH
            runtimeAgeMs <= 15_000L -> RuntimeFreshness.AGING
            else -> RuntimeFreshness.STALE
        }
    }
}
