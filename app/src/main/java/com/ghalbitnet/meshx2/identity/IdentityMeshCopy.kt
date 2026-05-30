package com.ghalbitnet.meshx2.identity

data class IdentityMeshCopy(
    val callId: String,
    val ownerDeviceId: String,
    val copyId: String,
    val copyIndex: Int,
    val maxCopies: Int = 10,
    val hopCount: Int = 0,
    val ttl: Long = DEFAULT_TTL_MS,
    val createdAt: Long = System.currentTimeMillis(),
    val lastForwardedAt: Long = createdAt,
    val hasReachedInternet: Boolean = false,
    val routeHint: String? = null,
    val routeScore: Int = 0
) {
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean =
        hasReachedInternet || ttl <= 0L || createdAt + ttl <= now

    companion object {
        const val DEFAULT_TTL_MS = 15 * 60 * 1000L
    }
}
