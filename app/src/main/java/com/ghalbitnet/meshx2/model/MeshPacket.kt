package com.ghalbitnet.meshx2.model
data class MeshPacket(
    val packetId: String, val source: String, val destination: String,
    val type: String, val payload: String, val hopCount: Int = 0,
    val maxHop: Int = 5, val timestamp: Long = System.currentTimeMillis(),
    val encrypted: Boolean = false
)