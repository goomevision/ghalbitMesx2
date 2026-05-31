package com.ghalbitnet.meshx2.verified.trust

object TrustBadgeRenderer {

    fun badge(score: Int): String {
        return when {
            score >= 81 -> "?? Pilar Komunitas"
            score >= 61 -> "?? Mentor"
            score >= 41 -> "?? Terpercaya"
            score >= 21 -> "?? Aktif"
            else -> "? Baru"
        }
    }
}
