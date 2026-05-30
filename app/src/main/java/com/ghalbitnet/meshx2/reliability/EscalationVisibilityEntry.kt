package com.ghalbitnet.meshx2.reliability

data class EscalationVisibilityEntry(
    val state: EscalationVisibilityState,
    val reason: String,
    val source: String,
    val confidence: String,
    val freshness: String,
    val blockers: String
)
