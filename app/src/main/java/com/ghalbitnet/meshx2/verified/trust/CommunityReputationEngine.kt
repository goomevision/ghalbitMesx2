package com.ghalbitnet.meshx2.verified.trust

object CommunityReputationEngine {

    fun calculate(
        memberCount: Int,
        activeMentors: Int,
        successfulReferrals: Int,
        contributionPoints: Int
    ): Int {
        val score =
            memberCount.coerceAtMost(25) +
            (activeMentors * 3).coerceAtMost(25) +
            (successfulReferrals * 2).coerceAtMost(25) +
            contributionPoints.coerceAtMost(25)

        return score.coerceIn(0, 100)
    }
}
