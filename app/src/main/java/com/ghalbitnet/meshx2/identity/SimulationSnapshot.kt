package com.ghalbitnet.meshx2.identity

data class SimulationSnapshot(
    val category: String,
    val summary: String,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null
)
