package com.ghalbitnet.meshx2.core.network

data class MeshPacket(
    val id: String,
    val source: String,
    val destination: String,
    val type: String,
    val payload: String,
    val timestamp: Long = System.currentTimeMillis(),
    val ttl: Int = 5
)
