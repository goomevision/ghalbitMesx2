package com.ghalbitnet.meshx2.identity

data class IdentityQualitySummary(
    val totalIdentities: Int,
    val strongCount: Int,
    val goodCount: Int,
    val partialCount: Int,
    val weakCount: Int,
    val legacyOnlyOrUnknownCount: Int,
    val averageScore: Int,
    val lowestScore: Int,
    val highestScore: Int
)
