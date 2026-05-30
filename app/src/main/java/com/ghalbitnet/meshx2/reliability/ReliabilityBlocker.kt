package com.ghalbitnet.meshx2.reliability

data class ReliabilityBlocker(
    val label: String,
    val severity: ReliabilityBlockerSeverity,
    val detail: String
)
