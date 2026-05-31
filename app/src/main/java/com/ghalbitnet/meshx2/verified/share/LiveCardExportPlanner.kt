package com.ghalbitnet.meshx2.verified.share

import com.ghalbitnet.meshx2.profile.ProfessionalCardTier

/**
 * PHASE 284E:
 * Planning-only structure for future animated export (MP4/GIF).
 * Current runtime still uses existing PNG/text share flow.
 */
enum class LiveCardExportFormat {
    PNG,
    GIF,
    MP4
}

data class LiveCardExportPlan(
    val enabled: Boolean,
    val format: LiveCardExportFormat,
    val durationMs: Long,
    val fps: Int,
    val includeQrGlow: Boolean,
    val includeBadgePulse: Boolean,
    val tier: ProfessionalCardTier
)

object LiveCardExportPlanner {
    fun forTier(tier: ProfessionalCardTier): LiveCardExportPlan {
        return LiveCardExportPlan(
            enabled = false,
            format = LiveCardExportFormat.PNG,
            durationMs = 2200L,
            fps = 24,
            includeQrGlow = true,
            includeBadgePulse = true,
            tier = tier
        )
    }
}
