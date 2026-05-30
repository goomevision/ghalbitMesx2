package com.ghalbitnet.meshx2.economy

data class RelayContribution(
    val nodeId: String,
    val relayedPacketCount: Int,
    val uptimeScore: Int,
    val bandwidthScore: Int,
    val updatedAt: Long = System.currentTimeMillis()
)
