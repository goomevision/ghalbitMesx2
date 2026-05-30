package com.ghalbitnet.meshx2.wallet

data class WalletOwner(
    val walletId: String,
    val displayName: String,
    val primary: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
