package com.ghalbitnet.meshx2.economy

data class UptimeScore(
    val nodeId: String,
    val score: Int,
    val sampledAt: Long = System.currentTimeMillis()
)
