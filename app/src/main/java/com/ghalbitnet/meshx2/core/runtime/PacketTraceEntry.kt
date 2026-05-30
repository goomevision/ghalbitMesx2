package com.ghalbitnet.meshx2.core.runtime

data class PacketTraceEntry(
    val packetType: String,
    val sourceNodeId: String,
    val targetNodeId: String,
    val routeType: String,
    val transport: String,
    val timestamp: Long = System.currentTimeMillis(),
    val deliveryState: String
)
