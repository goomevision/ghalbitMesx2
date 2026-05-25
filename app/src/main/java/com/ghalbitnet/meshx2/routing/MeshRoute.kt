package com.ghalbitnet.meshx2.routing

data class MeshRoute(
    val destination: String,
    val nextHop: String,
    val hopCount: Int,
    val updatedAt: Long =
        System.currentTimeMillis()
)
