package com.ghalbitnet.meshx2.wallet

data class NodeWalletBinding(
    val nodeId: String,
    val ownerWalletId: String,
    val rewardCollectionEnabled: Boolean = true,
    val boundAt: Long = System.currentTimeMillis(),
    val metadataVersion: Int = 1
)
