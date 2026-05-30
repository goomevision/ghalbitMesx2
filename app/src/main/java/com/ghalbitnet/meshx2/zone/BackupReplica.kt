package com.ghalbitnet.meshx2.zone

data class BackupReplica(
    val nodeId: String,
    val zoneId: String,
    val role: BackupNodeRole,
    val entryCount: Int,
    val exportedAt: Long = System.currentTimeMillis()
)
