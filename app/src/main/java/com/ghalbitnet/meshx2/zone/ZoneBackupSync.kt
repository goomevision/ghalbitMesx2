package com.ghalbitnet.meshx2.zone

data class ZoneBackupSync(
    val zoneId: String,
    val sourceNodeId: String,
    val targetNodeId: String,
    val exportedAt: Long = System.currentTimeMillis(),
    val importedAt: Long? = null
)
