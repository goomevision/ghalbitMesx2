package com.ghalbitnet.meshx2.verified.trust

data class CommunityLeaderboardEntry(
    val globalId: String,
    val displayName: String,
    val trustScore: Int
)

data class CommunityLeaderboard(
    val mentors: List<CommunityLeaderboardEntry> = emptyList(),
    val referrals: List<CommunityLeaderboardEntry> = emptyList(),
    val contributors: List<CommunityLeaderboardEntry> = emptyList()
)
