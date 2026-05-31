package com.ghalbitnet.meshx2.verified

data class VerifiedNameCardPayload(
    val version: Int = 1,
    val globalId: String,
    val displayName: String,
    val role: String? = null,
    val community: String? = null,
    val publicKeyHash: String,
    val profileHash: String,
    val cardHash: String? = null,
    val signature: String? = null,
    val centralVerifyUrl: String? = null,
    val localVerifyUrl: String? = null,
    val issuerNodeId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null
) {
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean {
        return expiresAt != null && now > expiresAt
    }

    fun isOfflineVerifiable(): Boolean {
        return !cardHash.isNullOrBlank() && !signature.isNullOrBlank()
    }
}
