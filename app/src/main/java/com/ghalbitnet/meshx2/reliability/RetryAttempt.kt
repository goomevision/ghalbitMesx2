package com.ghalbitnet.meshx2.reliability

data class RetryAttempt(
    val messageId: String,
    val category: RetryCategory,
    val policy: RetryPolicy,
    val attemptCount: Int,
    val createdAt: Long,
    val nextEligibleAt: Long?,
    val expiresAt: Long?
)
