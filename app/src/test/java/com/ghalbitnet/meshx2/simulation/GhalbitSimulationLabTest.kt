package com.ghalbitnet.meshx2.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
        world.registerDefaults()
        val messageId = "m-1"
        assertTrue(world.server.relaySend(world.peerB.peerId, messageId, fromPeerId = world.peerA.peerId, payload = "hello").ok)
        val (_, inboxOffline) = world.server.relayInbox(world.peerB.peerId)
        assertEquals(1, inboxOffline.size)
        world.bringPeerOnline(world.peerB)
        val sync = world.runVirtualPeerSync()
        assertEquals(1, sync.deliveredAcks)
        assertTrue(world.server.isDelivered(messageId))
    }

    @Test
    fun read_receipt_should_be_recorded() {
        val world = FakeGhalbitWorld()
        val messageId = "m-read-1"
        world.server.relaySend(world.peerC.peerId, messageId, fromPeerId = world.peerA.peerId, payload = "read")
        assertTrue(world.server.ackDelivered(messageId, world.peerC.peerId).ok)
        assertTrue(world.server.ackRead(messageId).ok)
        assertTrue(world.server.isRead(messageId))
    }

    @Test
    fun virtual_peer_should_reply_and_ack_in_logical_order() {
        val world = FakeGhalbitWorld()
        world.registerDefaults()
        world.bringPeerOnline(world.peerB)
        val messageId = "m-virtual-1"
        assertTrue(world.server.relaySend(world.peerB.peerId, messageId, fromPeerId = world.peerA.peerId, payload = "ping").ok)

        val sync = world.runVirtualPeerSync()

        assertEquals(1, sync.fetchedMessages)
        assertEquals(1, sync.deliveredAcks)
        assertEquals(1, sync.readAcks)
        assertEquals(1, sync.replyMessageIds.size)
        assertTrue(world.server.isDelivered(messageId))
        assertTrue(world.server.isRead(messageId))
        val reply = world.server.messageEnvelope(sync.replyMessageIds.first())
        assertNotNull(reply)
        assertEquals(world.peerB.peerId, reply?.fromPeerId)
        assertEquals(world.peerA.peerId, reply?.toPeerId)
    }

    @Test
    fun duplicate_message_id_should_not_create_duplicate_pending_queue() {
        val world = FakeGhalbitWorld()
        world.registerDefaults()
        val messageId = "m-dup-1"
        assertTrue(world.server.relaySend(world.peerB.peerId, messageId, fromPeerId = world.peerA.peerId, payload = "once").ok)
        assertTrue(world.server.relaySend(world.peerB.peerId, messageId, fromPeerId = world.peerA.peerId, payload = "twice").ok)
        assertEquals(1, world.server.pendingCount(world.peerB.peerId))
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
    fun virtual_peer_should_auto_accept_and_reply_with_tone_ping_pong() {
        val world = FakeGhalbitWorld()
        world.registerDefaults()
        world.bringPeerOnline(world.peerB)
        val callId = "c-tone-1"
        assertTrue(world.server.startCall(callId, fromPeerId = world.peerA.peerId, toPeerId = world.peerB.peerId).ok)
        val firstSync = world.runVirtualPeerSync(callId)
        assertEquals(1, firstSync.acceptedCalls)
        assertEquals("ACCEPTED", world.server.callStatus(callId))

        assertTrue(world.server.sendTone(callId, world.peerA.peerId, 440).ok)
        val secondSync = world.runVirtualPeerSync(callId)
        assertEquals(1, secondSync.toneReplies)

        val (_, inboxForA) = world.server.fetchToneInbox(world.peerA.peerId, callId)
        assertTrue(inboxForA.any { it.fromPeerId == world.peerB.peerId && it.hz == 660 })
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
