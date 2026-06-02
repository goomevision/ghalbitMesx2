package com.ghalbitnet.meshx2.simulation

class FakeGhalbitWorld {
    val clock = FakeClock(1_000L)
    val network = FakeNetworkCondition()
    val server = FakeOperatorServer(clock, network)
    val transport = FakeTransport(network)
    val loopGuard = LoopGuard()

    val peerA = FakePeer("A", online = true, mode = PeerMode.HYBRID)
    val peerB = FakePeer("B", online = false, mode = PeerMode.HYBRID, autoAcceptCalls = true, autoReadMessages = true, autoReplyPayload = "ACK_FROM_B", toneHz = 660)
    val peerC = FakePeer("C", online = true, mode = PeerMode.RELAY_ONLY)
    val virtualPeerB = VirtualPeerB(peerB, server, clock)

    fun bringPeerOnline(peer: FakePeer) {
        peer.online = true
        server.heartbeat(peer.peerId, online = true)
    }

    fun bringPeerOffline(peer: FakePeer) {
        peer.online = false
        server.heartbeat(peer.peerId, online = false)
    }

    fun registerDefaults() {
        server.registerDevice(peerA.peerId)
        server.registerDevice(peerB.peerId)
        server.registerDevice(peerC.peerId)
        server.heartbeat(peerA.peerId, online = peerA.online)
        server.heartbeat(peerB.peerId, online = peerB.online)
        server.heartbeat(peerC.peerId, online = peerC.online)
    }

    fun runVirtualPeerSync(activeCallId: String? = null): VirtualPeerSyncResult {
        return virtualPeerB.syncMessagesAndCalls(activeCallId)
    }
}
