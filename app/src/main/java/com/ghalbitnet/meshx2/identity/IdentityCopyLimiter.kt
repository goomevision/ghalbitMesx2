package com.ghalbitnet.meshx2.identity

object IdentityCopyLimiter {
    const val MAX_ACTIVE_COPIES = 10

    fun canCreateCopy(callId: String, copies: List<IdentityMeshCopy>): Boolean =
        getActiveCopyCount(callId, copies) < MAX_ACTIVE_COPIES

    fun registerCopy(copy: IdentityMeshCopy, copies: List<IdentityMeshCopy>): List<IdentityMeshCopy> {
        var next = pruneExpiredCopies(copies)
            .filterNot { it.copyId == copy.copyId }
            .toMutableList()
        next += copy
        while (getActiveCopyCount(copy.callId, next) > MAX_ACTIVE_COPIES) {
            next = expireWeakestCopy(copy.callId, next).toMutableList()
        }
        return next.sortedByDescending { it.createdAt }
    }

    fun expireOldestCopy(callId: String, copies: List<IdentityMeshCopy>): List<IdentityMeshCopy> {
        val target = copies.filter { it.callId == callId && !it.isExpired() }.minByOrNull { it.createdAt } ?: return copies
        return copies.map {
            if (it.copyId == target.copyId) {
                it.copy(ttl = 0L)
            } else {
                it
            }
        }
    }

    fun expireWeakestCopy(callId: String, copies: List<IdentityMeshCopy>): List<IdentityMeshCopy> {
        val target =
            copies
                .filter { it.callId == callId && !it.isExpired() }
                .minWithOrNull(compareBy<IdentityMeshCopy> { it.routeScore }.thenBy { it.createdAt })
                ?: return copies
        return copies.map {
            if (it.copyId == target.copyId) {
                it.copy(ttl = 0L)
            } else {
                it
            }
        }
    }

    fun pruneExpiredCopies(copies: List<IdentityMeshCopy>, now: Long = System.currentTimeMillis()): List<IdentityMeshCopy> =
        copies.filterNot { it.isExpired(now) }

    fun getActiveCopyCount(callId: String, copies: List<IdentityMeshCopy>): Int =
        copies.count { it.callId == callId && !it.isExpired() }
}
