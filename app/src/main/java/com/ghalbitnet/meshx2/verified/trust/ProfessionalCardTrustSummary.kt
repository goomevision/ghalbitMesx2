package com.ghalbitnet.meshx2.verified.trust

data class ProfessionalCardTrustSummary(
    val trustScore: Int,
    val trustRank: String,
    val mentorLevel: String,
    val referralLabel: String,
    val communityReputation: Int
)
