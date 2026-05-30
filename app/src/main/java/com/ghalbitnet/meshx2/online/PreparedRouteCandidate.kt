package com.ghalbitnet.meshx2.online

data class PreparedRouteCandidate(
    val sessionId: String,
    val peerGlobalId: String,
    val primaryRoute: String,
    val secondaryRoute: String,
    val relaySessionId: String? = null,
    val relayUrl: String? = null,
    val routeToken: String? = null,
    val preparedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 5 * 60 * 1000L,
    val healthScore: Int = 0,
    val lastValidatedAt: Long = 0L,
    val ready: Boolean = false,
    val state: SecondaryRouteState = SecondaryRouteState.MESH_ONLY
)
