package com.ghalbitnet.meshx2.verified.trust

object TrustScoreEngine {

    fun calculate(
        verified: Boolean,
        communityScore: Int,
        mentorScore: Int,
        referralScore: Int,
        reliabilityScore: Int
    ): Int {

        var score = 0

        if (verified) score += 25

        score += communityScore.coerceIn(0, 25)
        score += mentorScore.coerceIn(0, 20)
        score += referralScore.coerceIn(0, 15)
        score += reliabilityScore.coerceIn(0, 15)

        return score.coerceIn(0, 100)
    }
}
