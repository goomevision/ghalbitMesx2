package com.ghalbitnet.meshx2.verified.card

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * PHASE 263A
 * Persistent storage entity for GHALBIT verified cards.
 */
@Entity(tableName = "verified_cards")
data class CardStorageEntity(
    @PrimaryKey val globalId: String,
    val displayName: String,
    val role: String?,
    val community: String?,
    val publicKeyHash: String,
    val profileHash: String,
    val cardHash: String?,
    val signature: String?,
    val centralVerifyUrl: String?,
    val localVerifyUrl: String?,
    val issuerNodeId: String?,
    val createdAt: Long,
    val expiresAt: Long?,
    val version: Int,
    val updatedAt: Long,
    val syncStatus: String
)
