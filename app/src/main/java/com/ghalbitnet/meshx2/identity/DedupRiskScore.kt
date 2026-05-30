package com.ghalbitnet.meshx2.identity

data class DedupRiskScore(
    val score: Int,
    val category: String,
    val candidate: SoftDedupCandidate
)
