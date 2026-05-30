package com.ghalbitnet.meshx2.sos

data class SosAlert(
    val alertId: String,
    val sourceNodeId: String,
    val sourceGlobalId: String? = null,
    val receivedAt: Long,
    val message: String,
    val routeHint: String? = null,
    val isRead: Boolean = false,
    val relayPath: String? = null
)

