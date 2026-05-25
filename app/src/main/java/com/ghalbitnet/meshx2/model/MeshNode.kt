package com.ghalbitnet.meshx2.model
data class MeshNode(
    val name: String, val ipAddress: String, val publicKey: String = "",
    val latitude: Double = 0.0, val longitude: Double = 0.0,
    val signal: Int = 0, val latency: Int = 0, val trusted: Int = 50,
    val online: Boolean = false, val gateway: Boolean = false,
    val relay: Boolean = true, val balance: Double = 0.0,
    val lastSeen: Long = System.currentTimeMillis()
)