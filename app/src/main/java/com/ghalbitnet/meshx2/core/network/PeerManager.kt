package com.ghalbitnet.meshx2.core.network

object PeerManager {

    private val peers = mutableMapOf<String, String>()

    fun addPeer(peerId: String, ip: String) {
        peers[peerId] = ip
    }

    fun removePeer(peerId: String) {
        peers.remove(peerId)
    }

    fun getPeerIp(peerId: String): String? {
        return peers[peerId]
    }

    fun getAllPeers(): Map<String, String> {
        return peers
    }
}
