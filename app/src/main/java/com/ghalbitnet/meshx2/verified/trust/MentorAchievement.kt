package com.ghalbitnet.meshx2.verified.trust

data class MentorAchievement(
    val mentorGlobalId: String,
    val studentCount: Int,
    val workshopCount: Int,
    val achievementLevel: String,
    val updatedAt: Long
)
