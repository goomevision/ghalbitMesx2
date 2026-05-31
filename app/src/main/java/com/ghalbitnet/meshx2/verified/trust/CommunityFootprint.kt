package com.ghalbitnet.meshx2.verified.trust

data class CommunityFootprint(
    val communityId: String,
    val joinedAt: Long,
    val eventsParticipated: Int = 0,
    val projectsParticipated: Int = 0,
    val helpProvidedCount: Int = 0
)
