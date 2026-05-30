package com.ghalbitnet.meshx2.identity

data class ShadowMappingSummary(
    val totalMappings: Int,
    val highConfidenceCount: Int,
    val mediumConfidenceCount: Int,
    val lowConfidenceCount: Int,
    val conflictedCount: Int,
    val unknownCount: Int,
    val averageConfidence: Int
)
