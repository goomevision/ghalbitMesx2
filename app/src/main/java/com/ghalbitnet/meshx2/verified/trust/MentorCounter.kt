package com.ghalbitnet.meshx2.verified.trust

object MentorCounter {
    fun activeStudents(relations: List<MentorRelation>): Int =
        relations.count { it.active }
}
