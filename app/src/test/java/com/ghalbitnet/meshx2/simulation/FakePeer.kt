package com.ghalbitnet.meshx2.simulation

enum class PeerMode {
    INTERNET_ONLY,
    MESH_ONLY,
    RELAY_ONLY,
    HYBRID
}

data class FakePeer(
    val peerId: String,
    var online: Boolean = true,
    var mode: PeerMode = PeerMode.HYBRID
)

