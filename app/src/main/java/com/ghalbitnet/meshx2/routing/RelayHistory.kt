package com.ghalbitnet.meshx2.routing

data class RelayHistory(
    val destinationId: String,
    val relayNodeId: String,
    val successCount: Int,
    val lastRelayAt: Long = System.currentTimeMillis()
)
