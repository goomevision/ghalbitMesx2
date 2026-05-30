package com.ghalbitnet.meshx2.identity

data class IdentityConflictAssessment(
    val type: String,
    val severity: String,
    val suggestedAction: String,
    val confidence: Int
)
