package com.ghalbitnet.meshx2.verified.trust

enum class ReferralStatus {
    PENDING,
    ACTIVE,
    REWARDED
}

data class ReferralRecord(
    val referralId: String,
    val referrerGlobalId: String,
    val newUserGlobalId: String,
    val joinedAt: Long,
    val status: ReferralStatus = ReferralStatus.PENDING
)
