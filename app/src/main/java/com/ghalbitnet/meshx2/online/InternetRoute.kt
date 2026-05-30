package com.ghalbitnet.meshx2.online

data class InternetRoute(
    val targetGlobalId: String,
    val relayUrl: String,
    val transport: String = "internet",
    val active: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
