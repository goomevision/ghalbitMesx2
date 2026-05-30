package com.ghalbitnet.meshx2.routing

data class BestPathScore(
    val destinationId: String,
    val score: Int,
    val computedAt: Long = System.currentTimeMillis()
)
