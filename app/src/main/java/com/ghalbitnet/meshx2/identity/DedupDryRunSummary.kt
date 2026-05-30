package com.ghalbitnet.meshx2.identity

data class DedupDryRunSummary(
    val totalCandidates: Int,
    val highConfidenceCount: Int,
    val likelyCount: Int,
    val possibleCount: Int,
    val weakCount: Int,
    val unsafeOrAmbiguousCount: Int,
    val topRiskyConflicts: List<DedupRiskScore>,
    val topSafeCandidates: List<DedupRiskScore>
)
