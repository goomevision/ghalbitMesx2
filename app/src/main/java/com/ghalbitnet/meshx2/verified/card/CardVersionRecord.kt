package com.ghalbitnet.meshx2.verified.card

/**
 * PHASE 263E
 * Card version history record.
 */
data class CardVersionRecord(
    val globalId: String,
    val version: Int,
    val profileHash: String,
    val cardHash: String,
    val createdAt: Long,
    val changeReason: String
)
