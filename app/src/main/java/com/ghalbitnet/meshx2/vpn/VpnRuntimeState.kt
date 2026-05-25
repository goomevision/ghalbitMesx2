package com.ghalbitnet.meshx2.vpn

import java.util.concurrent.CopyOnWriteArraySet

/**
 * State runtime in-memory untuk detail VPN yang sedang berjalan.
 *
 * Kontrak saat ini:
 * - dipakai untuk menampilkan detail runtime terbaru ke UI
 * - bukan sumber persisted utama untuk mengetahui apakah service Android masih hidup
 * - akan reset ke default saat process mati / di-restart
 *
 * Lihat juga:
 * - `MeshVpnService.isBridgeServiceActive(context)` untuk flag persisted aktif/nonaktif
 * - `InternetBridgeStateManager` untuk status UI bridge tingkat produk
 *
 * TODO(core-stabilization):
 * Satukan kontrak read-model status VPN agar UI tidak perlu menebak dari tiga sumber
 * yang berbeda.
 */
object VpnRuntimeState {

    enum class ServiceStatus {
        VPN_ACTIVE,
        VPN_STOPPED
    }

    enum class GatewayStatus {
        GATEWAY_AVAILABLE,
        GATEWAY_NOT_AVAILABLE
    }

    enum class AccessStatus {
        ACCESS_ALLOWED,
        ACCESS_BLOCKED
    }

    data class Snapshot(
        val serviceStatus: ServiceStatus,
        val gatewayStatus: GatewayStatus,
        val activeGatewayName: String?,
        val accessStatus: AccessStatus,
        val packetInCount: Long,
        val packetAllowedCount: Long,
        val packetDroppedCount: Long,
        val packetsForwardedOut: Long,
        val lastDecision: String,
        val desiredRunning: Boolean,
        val updatedAt: Long,
        val lastRuntimeUpdateAt: Long,
        val runtimeAgeMs: Long
    ) {
        companion object {
            fun computeAge(lastRuntimeUpdateAt: Long, now: Long = System.currentTimeMillis()): Long {
                return if (lastRuntimeUpdateAt <= 0L) Long.MAX_VALUE else (now - lastRuntimeUpdateAt).coerceAtLeast(0L)
            }
        }
    }

    @Volatile
    private var serviceStatus: ServiceStatus = ServiceStatus.VPN_STOPPED

    @Volatile
    private var gatewayStatus: GatewayStatus = GatewayStatus.GATEWAY_NOT_AVAILABLE

    @Volatile
    private var accessStatus: AccessStatus = AccessStatus.ACCESS_BLOCKED

    @Volatile
    private var activeGatewayName: String? = null

    @Volatile
    private var packetInCount: Long = 0L

    @Volatile
    private var packetAllowedCount: Long = 0L

    @Volatile
    private var packetDroppedCount: Long = 0L

    @Volatile
    private var packetsForwardedOut: Long = 0L

    @Volatile
    private var lastDecision: String = "VPN belum aktif."

    @Volatile
    private var desiredRunning: Boolean = false

    @Volatile
    private var updatedAt: Long = 0L

    private val listeners =
        CopyOnWriteArraySet<(Snapshot) -> Unit>()

    fun markDesiredRunning(value: Boolean) {
        desiredRunning = value
        touch()
    }

    fun markServiceStatus(status: ServiceStatus, detail: String) {
        serviceStatus = status
        lastDecision = detail
        touch()
    }

    fun markGatewayStatus(status: GatewayStatus, detail: String) {
        gatewayStatus = status
        lastDecision = detail
        touch()
    }

    fun markActiveGatewayName(name: String?) {
        activeGatewayName = name?.takeIf { it.isNotBlank() }
        touch()
    }

    fun markAccessStatus(status: AccessStatus, detail: String) {
        accessStatus = status
        lastDecision = detail
        touch()
    }

    fun recordPacketIn() {
        packetInCount += 1
        touch()
    }

    fun recordAllowed(detail: String) {
        packetAllowedCount += 1
        lastDecision = detail
        accessStatus = AccessStatus.ACCESS_ALLOWED
        touch()
    }

    fun recordDropped(detail: String) {
        packetDroppedCount += 1
        lastDecision = detail
        accessStatus = AccessStatus.ACCESS_BLOCKED
        touch()
    }

    fun recordForwardedOut() {
        packetsForwardedOut += 1
        touch()
    }

    fun snapshot(): Snapshot {
        val now = System.currentTimeMillis()
        return Snapshot(
            serviceStatus = serviceStatus,
            gatewayStatus = gatewayStatus,
            activeGatewayName = activeGatewayName,
            accessStatus = accessStatus,
            packetInCount = packetInCount,
            packetAllowedCount = packetAllowedCount,
            packetDroppedCount = packetDroppedCount,
            packetsForwardedOut = packetsForwardedOut,
            lastDecision = lastDecision,
            desiredRunning = desiredRunning,
            updatedAt = updatedAt,
            lastRuntimeUpdateAt = updatedAt,
            runtimeAgeMs = Snapshot.computeAge(updatedAt, now)
        )
    }

    fun addListener(listener: (Snapshot) -> Unit) {
        listeners.add(listener)
        listener(snapshot())
    }

    fun removeListener(listener: (Snapshot) -> Unit) {
        listeners.remove(listener)
    }

    fun resetCounters() {
        packetInCount = 0L
        packetAllowedCount = 0L
        packetDroppedCount = 0L
        packetsForwardedOut = 0L
        activeGatewayName = null
        touch()
    }

    private fun touch() {
        updatedAt = System.currentTimeMillis()
        val snapshot = snapshot()
        listeners.forEach { it(snapshot) }
    }
}
