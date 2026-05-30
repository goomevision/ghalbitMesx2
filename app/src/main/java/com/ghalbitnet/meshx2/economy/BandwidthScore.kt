package com.ghalbitnet.meshx2.economy

data class BandwidthScore(
    val nodeId: String,
    val score: Int,
    val sampledAt: Long = System.currentTimeMillis()
)
