package com.ghalbitnet.meshx2.profile

import android.graphics.Color
import com.ghalbitnet.meshx2.verified.trust.ProfessionalCardTrustSummary

enum class ProfessionalCardTier {
    BASIC,
    TRUSTED,
    VERIFIED,
    MENTOR,
    COMMUNITY_LEADER,
    PREMIUM
}

data class ProfessionalCardTierTheme(
    val accentColor: Int,
    val badgeBgColor: Int,
    val badgeTextColor: Int,
    val routeTextColor: Int,
    val cardGlowColor: Int
)

object ProfessionalCardTierSystem {
    fun resolve(verified: Boolean, summary: ProfessionalCardTrustSummary): ProfessionalCardTier {
        val mentorActive = !summary.mentorLevel.equals("Belum Menjadi Mentor", ignoreCase = true)
        val (rewarded, active) = parseReferral(summary.referralLabel)
        val referralStrong = rewarded > 0 || active > 0
        return when {
            summary.trustScore >= 90 && mentorActive && summary.communityReputation >= 70 -> ProfessionalCardTier.PREMIUM
            summary.trustScore >= 75 || summary.communityReputation >= 60 -> ProfessionalCardTier.COMMUNITY_LEADER
            mentorActive -> ProfessionalCardTier.MENTOR
            verified -> ProfessionalCardTier.VERIFIED
            summary.trustScore >= 40 || referralStrong -> ProfessionalCardTier.TRUSTED
            else -> ProfessionalCardTier.BASIC
        }
    }

    fun themeFor(tier: ProfessionalCardTier): ProfessionalCardTierTheme {
        return when (tier) {
            ProfessionalCardTier.BASIC -> ProfessionalCardTierTheme(
                accentColor = Color.parseColor("#2C6FB8"),
                badgeBgColor = Color.parseColor("#1A3F6E"),
                badgeTextColor = Color.parseColor("#F5FBFF"),
                routeTextColor = Color.parseColor("#69F592"),
                cardGlowColor = Color.parseColor("#153A63")
            )
            ProfessionalCardTier.TRUSTED -> ProfessionalCardTierTheme(
                accentColor = Color.parseColor("#2E86DA"),
                badgeBgColor = Color.parseColor("#1D5A94"),
                badgeTextColor = Color.parseColor("#F5FBFF"),
                routeTextColor = Color.parseColor("#7CF5A3"),
                cardGlowColor = Color.parseColor("#1D4C7D")
            )
            ProfessionalCardTier.VERIFIED -> ProfessionalCardTierTheme(
                accentColor = Color.parseColor("#4AA8FF"),
                badgeBgColor = Color.parseColor("#C99A2E"),
                badgeTextColor = Color.parseColor("#FFF8E7"),
                routeTextColor = Color.parseColor("#D6ECFF"),
                cardGlowColor = Color.parseColor("#2A5D90")
            )
            ProfessionalCardTier.MENTOR -> ProfessionalCardTierTheme(
                accentColor = Color.parseColor("#3C9EE8"),
                badgeBgColor = Color.parseColor("#1E7A4F"),
                badgeTextColor = Color.parseColor("#EFFFF5"),
                routeTextColor = Color.parseColor("#9BF7C2"),
                cardGlowColor = Color.parseColor("#215E7E")
            )
            ProfessionalCardTier.COMMUNITY_LEADER -> ProfessionalCardTierTheme(
                accentColor = Color.parseColor("#5AA5FF"),
                badgeBgColor = Color.parseColor("#6F4BB5"),
                badgeTextColor = Color.parseColor("#F9F3FF"),
                routeTextColor = Color.parseColor("#E8D9FF"),
                cardGlowColor = Color.parseColor("#5D4B89")
            )
            ProfessionalCardTier.PREMIUM -> ProfessionalCardTierTheme(
                accentColor = Color.parseColor("#2D5FA0"),
                badgeBgColor = Color.parseColor("#1E3C69"),
                badgeTextColor = Color.parseColor("#F8FBFF"),
                routeTextColor = Color.parseColor("#D9ECFF"),
                cardGlowColor = Color.parseColor("#3B6AA8")
            )
        }
    }

    private fun parseReferral(referralLabel: String): Pair<Int, Int> {
        val cleaned = referralLabel.substringAfterLast(" ").trim()
        val chunks = cleaned.split("/")
        if (chunks.size != 2) return 0 to 0
        val rewarded = chunks[0].toIntOrNull() ?: 0
        val active = chunks[1].toIntOrNull() ?: 0
        return rewarded to active
    }
}
