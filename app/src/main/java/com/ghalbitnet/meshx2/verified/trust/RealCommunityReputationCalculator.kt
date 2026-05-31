package com.ghalbitnet.meshx2.verified.trust

object RealCommunityReputationCalculator {
    fun calculate(
        footprint: CommunityFootprint?,
        mentorCount: Int,
        referralCount: Int
    ): Int {
        return CommunityReputationEngine.calculate(
            memberCount = footprint?.helpProvidedCount ?: 0,
            activeMentors = mentorCount,
            successfulReferrals = referralCount,
            contributionPoints =
                (footprint?.eventsParticipated ?: 0) +
                (footprint?.projectsParticipated ?: 0)
        )
    }
}
