package com.ghalbitnet.meshx2.chat

enum class AdaptiveRouteType(val label: String) {
    LOCAL_MESH_DIRECT("Local Mesh"),
    LOCAL_RELAY("Relay Mesh"),
    NEARBY("Nearby"),
    INTERNET_RELAY("Internet Online"),
    PENDING_QUEUE("Offline Pending")
}
