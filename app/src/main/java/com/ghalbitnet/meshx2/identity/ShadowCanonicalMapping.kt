package com.ghalbitnet.meshx2.identity

data class ShadowCanonicalMapping(
    val legacyChatId: String,
    val canonicalGlobalId: String? = null,
    val publicKey: String? = null,
    val walletAddress: String? = null,
    val confidence: Int = 0,
    val source: String = "unknown",
    val riskLevel: String = "unknown",
    val lastSeenAt: Long = System.currentTimeMillis()
)
