package com.ghalbitnet.meshx2.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GhalbitSimulationLabTest {

    @Test
    fun server_truth_probe_endpoints_basic_flow() {
        val world = FakeGhalbitWorld()
        assertTrue(world.server.health().ok)
        assertTrue(world.server.registerDevice(world.peerA.peerId).ok)
        assertTrue(world.server.heartbeat(world.peerA.peerId).ok)
        val (lookupRes, found) = world.server.lookup(world.peerA.peerId)
        assertTrue(lookupRes.ok)
        assertTrue(found)
    }

    @Test
    fun pending_queue_peer_offline_then_online_receives_message() {
        val world = FakeGhalbitWorld()
        world.server.registerDevice(world.peerA.peerId)
        val messageId = "m-1"
        assertTrue(world.server.relaySend(world.peerB.peerId, messageId).ok)
        val (_, inboxOffline) = world.server.relayInbox(world.peerB.peerId)
        assertEquals(1, inboxOffline.size)
        world.bringPeerOnline(world.peerB)
        val deliveredRes = world.server.ackDelivered(messageId)
        assertTrue(deliveredRes.ok)
        assertTrue(world.server.isDelivered(messageId))
    }

    @Test
    fun read_receipt_should_be_recorded() {
        val world = FakeGhalbitWorld()
        val messageId = "m-read-1"
        world.server.relaySend(world.peerC.peerId, messageId)
        assertTrue(world.server.ackRead(messageId).ok)
        assertTrue(world.server.isRead(messageId))
    }

    @Test
    fun call_signaling_start_accept_end_should_not_stuck() {
        val world = FakeGhalbitWorld()
        val callId = "c-1"
        assertTrue(world.server.startCall(callId).ok)
        assertEquals("RINGING", world.server.callStatus(callId))
        assertTrue(world.server.acceptCall(callId).ok)
        assertEquals("ACCEPTED", world.server.callStatus(callId))
        assertTrue(world.server.endCall(callId).ok)
        assertEquals("ENDED", world.server.callStatus(callId))
    }

    @Test
    fun call_signaling_reject_flow_should_be_supported() {
        val world = FakeGhalbitWorld()
        val callId = "c-reject-1"
        assertTrue(world.server.startCall(callId).ok)
        assertTrue(world.server.rejectCall(callId).ok)
        assertEquals("REJECTED", world.server.callStatus(callId))
    }

    @Test
    fun server_error_500_should_not_crash_and_return_failed_status() {
        val world = FakeGhalbitWorld()
        world.network.serverErrorCode = 500
        val r = world.server.relaySend(world.peerB.peerId, "m-500")
        assertFalse(r.ok)
        assertEquals(500, r.httpCode)
    }

    @Test
    fun no_internet_should_fail_gracefully() {
        val world = FakeGhalbitWorld()
        world.network.internetAvailable = false
        val r = world.server.health()
        assertFalse(r.ok)
        assertEquals(0, r.httpCode)
    }

    @Test
    fun transport_should_prefer_internet_then_relay_fallback() {
        val world = FakeGhalbitWorld()
        val first = world.transport.sendChat(preferInternet = true)
        assertTrue(first.ok)
        assertEquals(TransportType.INTERNET, first.transport)

        world.network.internetAvailable = false
        val second = world.transport.sendChat(preferInternet = true)
        assertTrue(second.ok)
        assertEquals(TransportType.RELAY, second.transport)
    }

    @Test
    fun loop_guard_should_detect_retry_spam() {
        val guard = LoopGuard()
        repeat(20) { guard.track("retry_send", maxPerRun = 8) }
        val result = guard.assertNoLoop()
        assertFalse(result.ok)
        assertTrue(result.violations.isNotEmpty())
    }

    @Test
    fun loop_guard_should_pass_when_within_threshold() {
        val guard = LoopGuard()
        repeat(5) { guard.track("heartbeat", maxPerRun = 8) }
        val result = guard.assertNoLoop()
        assertTrue(result.ok)
    }
}

