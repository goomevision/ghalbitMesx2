package com.ghalbitnet.meshx2.future.vpn

/**
 * VPN POLICY MANAGER
 *
 * Tahap sekarang:
 * - aturan konseptual
 *
 * Masa depan:
 * - full tunnel
 * - split tunnel
 * - gateway otomatis
 * - tun2socks
 * - kontrol aplikasi mana yang lewat mesh
 */
object VpnPolicyManager {

    enum class Mode {
        OFF,
        LOCAL_MESH_ONLY,
        SPLIT_TUNNEL,
        FULL_TUNNEL
    }

    var currentMode: Mode = Mode.LOCAL_MESH_ONLY

    fun isInternetForwardingAllowed(): Boolean {
        return currentMode == Mode.FULL_TUNNEL ||
               currentMode == Mode.SPLIT_TUNNEL
    }

    fun shouldRouteThroughMesh(host: String): Boolean {
        return when (currentMode) {
            Mode.OFF -> false
            Mode.LOCAL_MESH_ONLY -> host.endsWith(".mesh")
            Mode.SPLIT_TUNNEL -> host.endsWith(".mesh")
            Mode.FULL_TUNNEL -> true
        }
    }
}
