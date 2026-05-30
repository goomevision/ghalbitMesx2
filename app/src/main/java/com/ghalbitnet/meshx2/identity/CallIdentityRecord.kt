package com.ghalbitnet.meshx2.identity

data class CallIdentityRecord(
    val callId: String,
    val userDisplayName: String,
    val publicKey: String,
    val deviceId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val syncState: IdentitySyncState = IdentitySyncState.LOCAL_ONLY,
    val lastServerSyncAt: Long = 0L,
    val lastMeshBroadcastAt: Long = 0L,
    val copyVersion: Int = 1,
    val signature: String? = null
)
