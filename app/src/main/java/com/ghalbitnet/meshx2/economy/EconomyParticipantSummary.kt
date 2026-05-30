package com.ghalbitnet.meshx2.economy

data class EconomyParticipantSummary(
    val totalParticipants: Int,
    val canonicalReadyCount: Int,
    val walletBasedCount: Int,
    val publicKeyBasedCount: Int,
    val nodeOrIpLegacyCount: Int,
    val unknownCount: Int,
    val averageConfidence: Int,
    val lowestConfidence: Int,
    val highestConfidence: Int
)
