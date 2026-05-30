package com.ghalbitnet.meshx2.reliability

data class ReliabilityAuditEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val reason: ReliabilityAuditReason,
    val detail: String
)
