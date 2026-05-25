package com.ghalbitnet.meshx2.core.config

/**
 * =====================================================
 * GHALBIT MESH X2
 * MESH CONFIG CENTER
 * =====================================================
 *
 * Pusat konfigurasi ringan.
 *
 * Ubah nilai di sini agar tidak perlu mengedit banyak file.
 *
 * FUTURE:
 * - remote config
 * - AI config tuning
 * - per-device optimization
 * - operator mode
 * - rural/offline mode
 * - emergency mode
 */

object MeshConfigCenter {

    // =================================================
    // NETWORK PORT
    // =================================================

    const val TCP_PORT: Int = 56565
    const val UDP_PORT: Int = 45454

    // =================================================
    // DISCOVERY
    // =================================================

    const val DISCOVERY_FAST_INTERVAL_MS: Long = 7000L
    const val DISCOVERY_NORMAL_INTERVAL_MS: Long = 10000L
    const val DISCOVERY_LOW_POWER_INTERVAL_MS: Long = 30000L

    // =================================================
    // HEARTBEAT
    // =================================================

    const val HEARTBEAT_NORMAL_MS: Long = 7000L
    const val HEARTBEAT_LOW_POWER_MS: Long = 20000L

    // =================================================
    // RECOVERY
    // =================================================

    const val RECOVERY_CHECK_MS: Long = 30000L
    const val RECOVERY_TIMEOUT_NORMAL_MS: Long = 45000L
    const val RECOVERY_TIMEOUT_LOW_POWER_MS: Long = 90000L

    // =================================================
    // FEATURE FLAGS
    // =================================================

    const val ENABLE_WIREGUARD: Boolean = true
    const val ENABLE_NEARBY: Boolean = true
    const val ENABLE_WIFI_DIRECT: Boolean = true
    const val ENABLE_AUTO_RECOVERY: Boolean = true
    const val ENABLE_HEALTH_REPORT: Boolean = true

    // =================================================
    // FUTURE FEATURE FLAGS
    // =================================================

    const val FUTURE_ENABLE_AI_ROUTING: Boolean = false
    const val FUTURE_ENABLE_QOS_ENGINE: Boolean = false
    const val FUTURE_ENABLE_TOKEN_REWARD: Boolean = false
    const val FUTURE_ENABLE_FEDERATED_LEARNING: Boolean = false
    const val FUTURE_ENABLE_SDR_BRIDGE: Boolean = false
    const val FUTURE_ENABLE_LORA_RELAY: Boolean = false
}
