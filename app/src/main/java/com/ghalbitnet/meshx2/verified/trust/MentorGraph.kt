package com.ghalbitnet.meshx2.verified.trust

data class MentorRelation(
    val mentorGlobalId: String,
    val studentGlobalId: String,
    val startedAt: Long,
    val active: Boolean = true
)

data class MentorGraph(
    val relations: List<MentorRelation> = emptyList()
)
