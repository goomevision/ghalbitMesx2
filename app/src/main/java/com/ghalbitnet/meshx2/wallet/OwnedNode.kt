package com.ghalbitnet.meshx2.wallet

data class OwnedNode(
    val nodeId: String,
    val ownerWalletId: String,
    val nodeLabel: String = nodeId,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
