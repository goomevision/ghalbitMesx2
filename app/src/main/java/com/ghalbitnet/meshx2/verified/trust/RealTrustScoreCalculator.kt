package com.ghalbitnet.meshx2.verified.trust

object RealTrustScoreCalculator {
    fun calculate(
        identity: VerifiedIdentityRecord,
        community: CommunityFootprint? = null,
        mentorRelations: List<MentorRelation> = emptyList(),
        referralRecords: List<ReferralRecord> = emptyList(),
        reliabilityScore: Int = 0
    ): Int {
        val verified = identity.identityLevel != IdentityLevel.UNVERIFIED || identity.publicKeyHash.isNotBlank()
        val communityScore = community?.let {
            (it.eventsParticipated * 2 + it.projectsParticipated * 4 + it.helpProvidedCount).coerceIn(0, 25)
        } ?: 0
        val mentorScore = (mentorRelations.count { it.active } * 4).coerceIn(0, 20)
        val referralScore = (referralRecords.count { it.status == ReferralStatus.ACTIVE || it.status == ReferralStatus.REWARDED } * 3).coerceIn(0, 15)
        return TrustScoreEngine.calculate(
            verified = verified,
            communityScore = communityScore,
            mentorScore = mentorScore,
            referralScore = referralScore,
            reliabilityScore = reliabilityScore.coerceIn(0, 15)
        )
    }
}
