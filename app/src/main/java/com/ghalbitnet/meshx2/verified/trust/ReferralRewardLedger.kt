package com.ghalbitnet.meshx2.verified.trust

data class ReferralRewardLedger(
    val referralId: String,
    val referrerGlobalId: String,
    val newUserGlobalId: String,
    val rewardGhbt: Double,
    val grantedAt: Long,
    val status: String
)
