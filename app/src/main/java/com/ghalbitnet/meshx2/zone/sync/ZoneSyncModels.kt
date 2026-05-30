package com.ghalbitnet.meshx2.zone.sync

data class ZoneSyncDelta(
    val scope: String,
    val changedEntries: Int,
    val lastSyncAt: Long = System.currentTimeMillis()
)

data class ZoneSyncResult(
    val success: Boolean,
    val scope: String,
    val deltaEntries: Int,
    val message: String
)
