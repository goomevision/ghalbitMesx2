package com.ghalbitnet.meshx2.future.qos

/**
 * DYNAMIC QOS MANAGER
 *
 * Tahap sekarang:
 * - klasifikasi trafik sederhana
 *
 * Masa depan:
 * - chat prioritas tinggi
 * - SOS prioritas paling tinggi
 * - file transfer dibatasi agar tidak berat
 * - video/audio adaptif
 */
object DynamicQoSManager {

    fun getPriority(packetType: String): Int {
        return when (packetType.uppercase()) {
            "SOS" -> 100
            "VOICE" -> 90
            "CHAT", "MESH_PACKET" -> 80
            "BLOCK_PROPOSAL" -> 60
            "FILE_CHUNK" -> 40
            else -> 30
        }
    }

    fun shouldDelay(packetType: String, networkBusy: Boolean): Boolean {
        if (!networkBusy) return false

        return getPriority(packetType) < 50
    }
}
