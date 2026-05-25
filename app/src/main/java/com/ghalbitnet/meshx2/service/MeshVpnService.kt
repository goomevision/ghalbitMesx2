package com.ghalbitnet.meshx2.service

import android.content.Context

/**
 * Entry-point service yang terdaftar di AndroidManifest dan menjadi target semua
 * Intent start/stop dari controller aplikasi.
 *
 * Saat ini kelas ini sengaja tipis:
 * - menjadi nama service publik/stabil untuk manifest dan caller
 * - mewarisi seluruh implementasi runtime dari [GhalbitVpnService]
 *
 * TODO(core-stabilization):
 * Putuskan secara eksplisit apakah kelas ini akan tetap menjadi façade publik
 * permanen, atau nantinya implementasi utama dipindahkan ke nama yang lebih
 * netral setelah seluruh caller dan manifest disatukan.
 */
class MeshVpnService : GhalbitVpnService() {
    companion object {
        const val ACTION_START_BRIDGE_MONITOR = ACTION_START_VPN
        const val ACTION_STOP_BRIDGE_MONITOR = ACTION_STOP_VPN

        fun isBridgeServiceActive(context: Context): Boolean {
            return isVpnActive(context)
        }
    }
}
