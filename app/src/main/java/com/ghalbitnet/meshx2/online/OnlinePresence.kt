package com.ghalbitnet.meshx2.online

data class OnlinePresence(
    val nodeId: String,
    val globalId: String,
    val publicKeyHash: String? = null,
    val online: Boolean = true,
    val route: InternetRoute? = null,
    val lastSeen: Long = System.currentTimeMillis()
)
