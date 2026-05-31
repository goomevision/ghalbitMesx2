package com.ghalbitnet.meshx2.verified.trust

data class ReputationSnapshot(
    val timestamp: Long,
    val trustScore: Int,
    val communityRank: Int,
    val mentorRank: Int
)
