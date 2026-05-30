package com.ghalbitnet.meshx2.identity

data class MigrationDryRunCandidate(
    val legacyChatId: String,
    val primaryLabel: String,
    val secondaryLabel: String?,
    val canonicalReference: String?,
    val confidence: Int,
    val riskLevel: String,
    val classification: String,
    val reason: String,
    val rollbackRequirement: String,
    val mappingSource: String
)
