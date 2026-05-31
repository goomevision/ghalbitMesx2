package com.ghalbitnet.meshx2.verified.trust

object TrustRankCalculator {

    fun rank(score: Int): String {
        return when {
            score >= 90 -> "Pilar Komunitas"
            score >= 75 -> "Mentor Utama"
            score >= 60 -> "Terpercaya"
            score >= 40 -> "Aktif"
            score >= 20 -> "Pemula"
            else -> "Baru"
        }
    }
}
