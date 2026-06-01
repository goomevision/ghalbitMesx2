package com.ghalbitnet.meshx2.simulation

class FakeGhalbitWorld {
    val clock = FakeClock(1_000L)
    val network = FakeNetworkCondition()
    val server = FakeOperatorServer(clock, network)
    val transport = FakeTransport(network)
    val loopGuard = LoopGuard()

    val peerA = FakePeer("A", online = true, mode = PeerMode.HYBRID)
    val peerB = FakePeer("B", online = false, mode = PeerMode.HYBRID)
    val peerC = FakePeer("C", online = true, mode = PeerMode.RELAY_ONLY)

    fun bringPeerOnline(peer: FakePeer) {
        peer.online = true
    }

    fun bringPeerOffline(peer: FakePeer) {
        peer.online = false
    }
}

