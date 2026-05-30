package com.ghalbitnet.meshx2.reliability

data class GovernanceChecklistItem(
    val label: String,
    val status: GovernanceChecklistStatus,
    val detail: String
)
