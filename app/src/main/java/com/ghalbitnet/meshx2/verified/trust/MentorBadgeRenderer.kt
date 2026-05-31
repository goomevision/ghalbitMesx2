package com.ghalbitnet.meshx2.verified.trust

object MentorBadgeRenderer {

    fun level(studentCount: Int): String {
        return when {
            studentCount >= 100 -> "Mentor Level 5"
            studentCount >= 50 -> "Mentor Level 4"
            studentCount >= 20 -> "Mentor Level 3"
            studentCount >= 10 -> "Mentor Level 2"
            studentCount >= 1 -> "Mentor Level 1"
            else -> "Belum Menjadi Mentor"
        }
    }
}
