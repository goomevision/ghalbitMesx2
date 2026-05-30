package com.ghalbitnet.meshx2.identity

data class RoutingIdentitySummary(
    val totalRoutesInspected: Int,
    val canonicalReadyCount: Int,
    val mixedCount: Int,
    val conflictedCount: Int,
    val legacyOnlyCount: Int,
    val averageConfidence: Int
)
