package com.ghalbitnet.meshx2.verified.trust

data class ReferralBadge(
    val activeReferrals: Int,
    val rewardedReferrals: Int
)

object ReferralBadgeRenderer {

    fun label(badge: ReferralBadge): String {
        return "Referral ${badge.rewardedReferrals}/${badge.activeReferrals}"
    }
}
