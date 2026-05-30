package com.ghalbitnet.meshx2.zone

data class LocalZoneLedger(
    val zoneId: String,
    val entries: List<ZoneLedgerEntry>,
    val updatedAt: Long = System.currentTimeMillis()
)
