package com.ghalbitnet.meshx2.verified.trust

object ProfessionalCardSummaryFactory {

    fun create(
        trustScore: Int,
        mentorCount: Int,
        referralActive: Int,
        referralRewarded: Int,
        reputation: Int
    ): ProfessionalCardTrustSummary {

        return ProfessionalCardTrustSummary(
            trustScore = trustScore,
            trustRank = TrustRankCalculator.rank(trustScore),
            mentorLevel = MentorBadgeRenderer.level(mentorCount),
            referralLabel = "$referralRewarded/$referralActive",
            communityReputation = reputation
        )
    }
}
