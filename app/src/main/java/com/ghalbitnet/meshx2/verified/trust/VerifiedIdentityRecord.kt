package com.ghalbitnet.meshx2.verified.trust

/**
 * PHASE 275A
 * Core verified digital identity record for GHALBITNET cards.
 */
data class VerifiedIdentityRecord(
    val globalId: String,
    val publicKeyHash: String,
    val displayName: String,
    val community: String,
    val role: String,
    val createdAt: Long,
    val verifiedAt: Long? = null,
    val identityLevel: IdentityLevel = IdentityLevel.UNVERIFIED
)

enum class IdentityLevel {
    UNVERIFIED,
    COMMUNITY_VERIFIED,
    MENTOR_VERIFIED,
    MULTI_VERIFIED
}
