package com.ghalbitnet.meshx2.future.reward

/**
 * REWARD ENGINE
 *
 * Tahap sekarang:
 * - hitung reward sederhana
 *
 * Masa depan:
 * - Proof of Connectivity
 * - Proof of Relay
 * - Proof of Storage
 * - Proof of Intelligence
 */
object RewardEngine {

    fun calculateRelayReward(
        bytesForwarded: Long,
        success: Boolean,
        latencyMs: Long
    ): Double {

        if (!success) return 0.0

        val base = 0.001
        val sizeReward = bytesForwarded / 1024.0 / 1024.0 * 0.01
        val latencyBonus = if (latencyMs < 100) 0.005 else 0.0

        return base + sizeReward + latencyBonus
    }
}
