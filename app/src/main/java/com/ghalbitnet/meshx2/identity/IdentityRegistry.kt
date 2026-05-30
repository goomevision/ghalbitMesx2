package com.ghalbitnet.meshx2.identity

import java.util.concurrent.ConcurrentHashMap

object IdentityRegistry {

    private val records =
        ConcurrentHashMap<String, GhalbitIdentityRecord>()

    fun upsert(record: GhalbitIdentityRecord): GhalbitIdentityRecord {
        return records.compute(record.globalId) { _, existing ->
            IdentityBridge.merge(existing, record)
        } ?: record
    }

    fun get(globalId: String): GhalbitIdentityRecord? {
        return records[globalId]
    }

    fun resolveForChatTarget(
        globalId: String? = null,
        peerName: String? = null,
        ipAddress: String? = null,
        publicKey: String? = null,
        walletAddress: String? = null
    ): GhalbitIdentityRecord? {
        if (!globalId.isNullOrBlank()) {
            records[globalId]?.let { return it }
        }

        return records.values.firstOrNull { record ->
            (!publicKey.isNullOrBlank() && record.publicKey == publicKey) ||
                (!walletAddress.isNullOrBlank() && record.walletAddress == walletAddress) ||
                (!peerName.isNullOrBlank() && record.displayName == peerName) ||
                (!ipAddress.isNullOrBlank() && record.lastKnownIp == ipAddress)
        }
    }

    fun findByLegacy(
        peerName: String? = null,
        ipAddress: String? = null,
        publicKey: String? = null
    ): GhalbitIdentityRecord? {
        return resolveForChatTarget(
            peerName = peerName,
            ipAddress = ipAddress,
            publicKey = publicKey
        )
    }

    fun all(): List<GhalbitIdentityRecord> {
        return records.values
            .sortedByDescending { it.lastSeen }
    }

    fun clear() {
        records.clear()
    }
}
