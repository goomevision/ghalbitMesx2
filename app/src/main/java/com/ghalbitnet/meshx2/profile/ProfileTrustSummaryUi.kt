package com.ghalbitnet.meshx2.profile

data class ProfileTrustSummaryUi(
    val trustScore: Int = 0,
    val rank: String = "Baru",
    val mentorLabel: String = "Belum Menjadi Mentor",
    val referralLabel: String = "0/0",
    val reputation: Int = 0
)
