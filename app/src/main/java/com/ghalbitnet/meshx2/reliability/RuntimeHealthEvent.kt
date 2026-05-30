package com.ghalbitnet.meshx2.reliability

data class RuntimeHealthEvent(
    val category: String,
    val value: String,
    val trend: RuntimeHealthTrend,
    val timestampLabel: String
)
