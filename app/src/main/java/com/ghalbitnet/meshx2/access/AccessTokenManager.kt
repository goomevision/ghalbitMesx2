package com.ghalbitnet.meshx2.access

import com.ghalbitnet.meshx2.vpn.VpnLogManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object AccessTokenManager {

    data class AccessToken(
        val token: String,
        val nodeId: String,
        val issuedAt: Long,
        val expiresAt: Long
    )

    private val activeTokens = ConcurrentHashMap<String, AccessToken>()
    private val authReuseCount = AtomicLong(0L)
    private val authRefreshCount = AtomicLong(0L)

    data class TokenLifecycle(
        val token: AccessToken,
        val reused: Boolean
    )

    data class TokenStats(
        val authReuseCount: Long,
        val authRefreshCount: Long
    )

    fun issueToken(nodeId: String): AccessToken {
        cleanupExpired()
        val issuedAt = System.currentTimeMillis()
        val token =
            AccessToken(
                token = "atk-${UUID.randomUUID()}",
                nodeId = nodeId,
                issuedAt = issuedAt,
                expiresAt = issuedAt + NetworkAccessPolicy.ACCESS_TOKEN_TTL_MS
            )
        activeTokens[nodeId] = token
        return token
    }

    fun issueOrReuseToken(
        nodeId: String,
        refreshRequired: Boolean = false
    ): TokenLifecycle {
        cleanupExpired()
        val existing = activeTokens[nodeId]
        if (existing != null && !refreshRequired && existing.expiresAt > System.currentTimeMillis()) {
            authReuseCount.incrementAndGet()
            return TokenLifecycle(existing, reused = true)
        }
        if (existing != null && existing.expiresAt <= System.currentTimeMillis()) {
            authRefreshCount.incrementAndGet()
        } else if (refreshRequired) {
            authRefreshCount.incrementAndGet()
        }
        return TokenLifecycle(issueToken(nodeId), reused = false)
    }

    fun getValidToken(nodeId: String): AccessToken? {
        cleanupExpired()
        return activeTokens[nodeId]
    }

    fun findByToken(rawToken: String): AccessToken? {
        cleanupExpired()
        return activeTokens.values.firstOrNull { it.token == rawToken }
    }

    fun expireToken(nodeId: String) {
        activeTokens.remove(nodeId)
    }

    fun stats(): TokenStats =
        TokenStats(
            authReuseCount = authReuseCount.get(),
            authRefreshCount = authRefreshCount.get()
        )

    fun cleanupExpired(now: Long = System.currentTimeMillis()) {
        activeTokens.values
            .filter { it.expiresAt <= now }
            .forEach {
                activeTokens.remove(it.nodeId)
                VpnLogManager.info(
                    "ACCESS_TOKEN_EXPIRED",
                    "nodeId=${it.nodeId} token=${it.token.take(12)}"
                )
            }
    }
}
