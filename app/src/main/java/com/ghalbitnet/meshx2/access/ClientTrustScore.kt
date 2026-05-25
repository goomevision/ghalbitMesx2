package com.ghalbitnet.meshx2.access

data class ClientTrustScore(
    val score: Int,
    val level: ClientTrustLevel,
    val reasons: List<String>
)
