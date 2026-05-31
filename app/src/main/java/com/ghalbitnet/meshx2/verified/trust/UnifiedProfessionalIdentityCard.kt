package com.ghalbitnet.meshx2.verified.trust

object UnifiedProfessionalIdentityCard {

    fun render(
        displayName: String,
        community: String,
        verified: Boolean,
        summary: ProfessionalCardTrustSummary
    ): String {
        return """
            GHALBIT VERIFIED CARD

            $displayName
            $community

            Trust Score : ${summary.trustScore}
            Rank        : ${summary.trustRank}

            Mentor      : ${summary.mentorLevel}
            Referral    : ${summary.referralLabel}

            Community Reputation : ${summary.communityReputation}

            ${if (verified) "VERIFIED ?" else "UNVERIFIED"}
        """.trimIndent()
    }
}
