package com.ghalbitnet.meshx2.verified.referral

/**
 * PHASE 259A
 * Referral identity attached to a verified card.
 */
data class ReferralIdentityPayload(
    val referralCode: String,
    val referrerGlobalId: String,
    val referrerDisplayName: String,
    val communityId: String? = null,
    val role: String = "REFERRER",
    val createdAt: Long = System.currentTimeMillis()
)
