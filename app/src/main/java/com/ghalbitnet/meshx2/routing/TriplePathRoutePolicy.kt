package com.ghalbitnet.meshx2.routing

object TriplePathRoutePolicy {
    const val SERVER_DIRECT_INTERNET = "SERVER_DIRECT_INTERNET"
    const val INTERNET_RELAY = "INTERNET_RELAY"
    const val LOCAL_MESH_PRIMARY = "LOCAL_MESH_PRIMARY"
    const val LOCAL_MESH_SECONDARY = "LOCAL_MESH_SECONDARY"
    const val IDENTITY_COPY_TRACE = "IDENTITY_COPY_TRACE"
    const val STORE_FORWARD = "STORE_FORWARD"

    fun baseScore(routeType: String): Int =
        when (routeType) {
            SERVER_DIRECT_INTERNET -> 100
            INTERNET_RELAY -> 90
            LOCAL_MESH_PRIMARY -> 82
            LOCAL_MESH_SECONDARY -> 72
            IDENTITY_COPY_TRACE -> 58
            STORE_FORWARD -> 20
            else -> 10
        }
}
