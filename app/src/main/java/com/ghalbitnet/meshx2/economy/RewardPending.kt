package com.ghalbitnet.meshx2.economy

data class RewardPending(
    val nodeId: String,
    val amount: Double,
    val reason: String,
    val createdAt: Long = System.currentTimeMillis()
)
