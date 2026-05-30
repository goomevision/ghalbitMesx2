package com.ghalbitnet.meshx2.reliability

data class RuntimeStressIndicator(
    val name: String,
    val severity: RuntimeStressSeverity,
    val detail: String
)
