package com.ghalbitnet.meshx2.zone

data class ZoneLedgerEntry(
    val nodeId: String,
    val zoneId: String,
    val publicKeyHash: String,
    val lastSeen: Long,
    val routeHint: String? = null,
    val trustScore: Int = 50,
    val expireAt: Long = 0L
)
