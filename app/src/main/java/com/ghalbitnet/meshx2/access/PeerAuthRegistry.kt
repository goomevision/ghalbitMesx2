package com.ghalbitnet.meshx2.access

import java.util.concurrent.ConcurrentHashMap

object PeerAuthRegistry {

    private const val IDENTICAL_UPDATE_DEBOUNCE_MS = 30_000L

    data class PeerAuthRecord(
        val nodeId: String,
        val publicKey: String,
        val walletAddress: String,
        val appVersion: String,
        val ipAddress: String,
        val port: Int,
        val status: NetworkAccessPolicy.AuthStatus,
        val accessToken: String?,
        val reason: String,
        val lastSeen: Long,
        val expiresAt: Long = 0L
    )

    private val authRecords = ConcurrentHashMap<String, PeerAuthRecord>()
    private val blockedNodes = ConcurrentHashMap<String, String>()
    private val usedNonces = ConcurrentHashMap<String, Long>()

    fun upsert(record: PeerAuthRecord) {
        val existing = authRecords[record.nodeId]
        if (
            existing != null &&
            existing.ipAddress == record.ipAddress &&
            existing.port == record.port &&
            existing.publicKey == record.publicKey &&
            existing.walletAddress == record.walletAddress &&
            existing.status == record.status &&
            existing.accessToken == record.accessToken &&
            System.currentTimeMillis() - existing.lastSeen < IDENTICAL_UPDATE_DEBOUNCE_MS
        ) {
            return
        }
        authRecords[record.nodeId] = record
    }

    fun markBlocked(nodeId: String, reason: String) {
        blockedNodes[nodeId] = reason
        authRecords[nodeId] =
            authRecords[nodeId]?.copy(
                status = NetworkAccessPolicy.AuthStatus.BLOCKED,
                reason = reason,
                lastSeen = System.currentTimeMillis()
            ) ?: PeerAuthRecord(
                nodeId = nodeId,
                publicKey = "",
                walletAddress = "",
                appVersion = "",
                ipAddress = "",
                port = NetworkAccessPolicy.DEFAULT_MESH_SOCKET_PORT,
                status = NetworkAccessPolicy.AuthStatus.BLOCKED,
                accessToken = null,
                reason = reason,
                lastSeen = System.currentTimeMillis()
            )
    }

    fun isBlocked(nodeId: String): Boolean = blockedNodes.containsKey(nodeId)

    fun blockedReason(nodeId: String): String? = blockedNodes[nodeId]

    fun get(nodeId: String): PeerAuthRecord? {
        cleanupExpired()
        return authRecords[nodeId]
    }

    fun getByIp(ipAddress: String): PeerAuthRecord? {
        cleanupExpired()
        return authRecords.values.firstOrNull {
            it.ipAddress == ipAddress && it.status == NetworkAccessPolicy.AuthStatus.AUTHORIZED
        }
    }

    fun all(): List<PeerAuthRecord> {
        cleanupExpired()
        return authRecords.values.sortedByDescending { it.lastSeen }
    }

    fun isAuthorized(nodeId: String): Boolean {
        cleanupExpired()
        return authRecords[nodeId]?.status == NetworkAccessPolicy.AuthStatus.AUTHORIZED
    }

    fun noteNonce(nodeId: String, nonce: String, timestamp: Long) {
        usedNonces["$nodeId|$nonce"] = timestamp
    }

    fun hasSeenNonce(nodeId: String, nonce: String): Boolean {
        cleanupExpired()
        return usedNonces.containsKey("$nodeId|$nonce")
    }

    fun cleanupExpired(now: Long = System.currentTimeMillis()) {
        AccessTokenManager.cleanupExpired(now)
        authRecords.entries.forEach { entry ->
            val record = entry.value
            if (record.expiresAt > 0L && record.expiresAt <= now && record.status == NetworkAccessPolicy.AuthStatus.AUTHORIZED) {
                entry.setValue(
                    record.copy(
                        status = NetworkAccessPolicy.AuthStatus.EXPIRED,
                        accessToken = null,
                        reason = "Access token expired",
                        lastSeen = now
                    )
                )
            }
        }
        usedNonces.entries.removeIf { (_, seenAt) ->
            now - seenAt > NetworkAccessPolicy.ACCESS_TOKEN_TTL_MS
        }
    }
}
