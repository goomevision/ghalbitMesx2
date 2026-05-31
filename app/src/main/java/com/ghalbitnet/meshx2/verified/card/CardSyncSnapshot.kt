package com.ghalbitnet.meshx2.verified.card

/**
 * PHASE 263D
 * Tracks card synchronization state.
 */
data class CardSyncSnapshot(
    val globalId: String,
    val localVersion: Int,
    val remoteVersion: Int,
    val lastSyncAt: Long,
    val syncState: String
)
