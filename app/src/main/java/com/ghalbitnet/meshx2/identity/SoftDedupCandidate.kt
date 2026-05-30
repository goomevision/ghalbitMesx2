package com.ghalbitnet.meshx2.identity

data class SoftDedupCandidate(
    val strength: String,
    val reason: String,
    val leftLabel: String,
    val rightLabel: String,
    val leftReference: String? = null,
    val rightReference: String? = null,
    val sameWalletAddress: Boolean = false,
    val samePublicKey: Boolean = false,
    val sameGlobalId: Boolean = false,
    val sameConversationStoreMapping: Boolean = false,
    val sameIdentityRegistryMapping: Boolean = false,
    val sameIp: Boolean = false,
    val sameDisplayName: Boolean = false,
    val conflictingWalletAddress: Boolean = false,
    val conflictingPublicKey: Boolean = false,
    val conflictingGlobalId: Boolean = false
)
