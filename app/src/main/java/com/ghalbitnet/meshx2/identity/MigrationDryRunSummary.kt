package com.ghalbitnet.meshx2.identity

data class MigrationDryRunSummary(
    val totalCandidates: Int,
    val safeCount: Int,
    val riskyCount: Int,
    val blockedCount: Int,
    val averageConfidence: Int
)
