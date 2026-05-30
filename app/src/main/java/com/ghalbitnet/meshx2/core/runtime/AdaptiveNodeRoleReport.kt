package com.ghalbitnet.meshx2.core.runtime

data class AdaptiveNodeRoleReport(
    val role: AdaptiveNodeRole,
    val batteryPercent: Int,
    val charging: Boolean,
    val hotspotLikelyActive: Boolean,
    val memoryClassMb: Int,
    val networkConnected: Boolean,
    val reason: String
)
