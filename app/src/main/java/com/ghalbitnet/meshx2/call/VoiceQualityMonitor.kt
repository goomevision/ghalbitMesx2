package com.ghalbitnet.meshx2.call

import android.util.Log

enum class VoiceQualityScore {
    EXCELLENT,
    GOOD,
    DEGRADED,
    CRITICAL,
    LOST
}

data class VoiceQualitySnapshot(
    val packetLoss: Int,
    val jitterMs: Int,
    val audioGapMs: Long,
    val queueDelayMs: Long,
    val score: VoiceQualityScore
)

object VoiceQualityMonitor {
    fun evaluate(packetLoss: Int, jitterMs: Int, audioGapMs: Long, queueDelayMs: Long): VoiceQualitySnapshot {
        val score =
            when {
                audioGapMs >= 8_000L -> VoiceQualityScore.LOST
                packetLoss >= 45 || audioGapMs >= 5_000L || queueDelayMs >= 4_000L -> VoiceQualityScore.CRITICAL
                packetLoss >= 20 || jitterMs >= 140 || audioGapMs >= 1_500L -> VoiceQualityScore.DEGRADED
                packetLoss >= 8 || jitterMs >= 80 -> VoiceQualityScore.GOOD
                else -> VoiceQualityScore.EXCELLENT
            }
        Log.d("GHALBIT-VOICE-QUALITY", "score=$score")
        Log.d("GHALBIT-VOICE-QUALITY", "packetLoss=$packetLoss")
        Log.d("GHALBIT-VOICE-QUALITY", "jitter=$jitterMs")
        if (score == VoiceQualityScore.CRITICAL) {
            Log.w("GHALBIT-VOICE-QUALITY", "critical")
        }
        return VoiceQualitySnapshot(packetLoss, jitterMs, audioGapMs, queueDelayMs, score)
    }
}
