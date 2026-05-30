package com.ghalbitnet.meshx2.identity

data class DryRunGateStatus(
    val category: String,
    val identityAverage: Int,
    val dedupUnsafeCount: Int,
    val shadowAverage: Int,
    val economyAverage: Int,
    val resolverCoverage: String,
    val rollbackReadiness: String,
    val diagnosticsCompleteness: String,
    val notes: List<String>
)
