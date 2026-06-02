package com.ghalbitnet.meshx2.simulation

enum class EndpointStatus {
    READY,
    PARTIAL,
    CODE_ONLY,
    MISSING,
    FAILED
}

data class EndpointResult(
    val endpoint: String,
    val ok: Boolean,
    val httpCode: Int,
    val latencyMs: Long,
    val error: String? = null
)

data class PresenceSnapshot(
    val peerId: String,
    val online: Boolean,
    val lastSeenMs: Long,
    val status: String
)

data class MessageEnvelope(
    val messageId: String,
    val fromPeerId: String,
    val toPeerId: String,
    val payload: String,
    val createdAtMs: Long,
    var delivered: Boolean = false,
    var read: Boolean = false
)

data class CallSessionSnapshot(
    val callId: String,
    val fromPeerId: String,
    val toPeerId: String,
    var status: String,
    val startedAtMs: Long,
    val tones: MutableList<ToneFrame> = mutableListOf()
)

data class ToneFrame(
    val fromPeerId: String,
    val toPeerId: String,
    val hz: Int,
    val durationMs: Long,
    val createdAtMs: Long
)

class FakeOperatorServer(
    private val clock: FakeClock,
    private val net: FakeNetworkCondition
) {
    private val registeredPeers = mutableMapOf<String, PresenceSnapshot>()
    private val pendingByPeer = mutableMapOf<String, MutableList<MessageEnvelope>>()
    private val allMessages = linkedMapOf<String, MessageEnvelope>()
    private val delivered = mutableSetOf<String>()
    private val read = mutableSetOf<String>()
    private val callState = mutableMapOf<String, CallSessionSnapshot>()

    fun health(): EndpointResult = endpoint("/health")
    fun registerDevice(peerId: String, publicKey: String = ""): EndpointResult {
        val res = endpoint("/identity/register")
        if (res.ok) {
            registeredPeers[peerId] =
                PresenceSnapshot(
                    peerId = peerId,
                    online = true,
                    lastSeenMs = clock.nowMs,
                    status = if (publicKey.isBlank()) "REGISTERED" else "REGISTERED_WITH_KEY"
                )
        }
        return res
    }

    fun heartbeat(peerId: String, online: Boolean = true): EndpointResult {
        val res = endpoint("/presence/heartbeat")
        if (res.ok) {
            registeredPeers[peerId] =
                PresenceSnapshot(
                    peerId = peerId,
                    online = online,
                    lastSeenMs = clock.nowMs,
                    status = if (online) "ONLINE" else "OFFLINE"
                )
        }
        return res
    }

    fun lookup(peerId: String): Pair<EndpointResult, Boolean> {
        val res = endpoint("/identity/lookup/$peerId")
        return res to registeredPeers.containsKey(peerId)
    }

    fun getPresence(peerId: String): Pair<EndpointResult, PresenceSnapshot?> {
        val res = endpoint("/presence/$peerId")
        return res to registeredPeers[peerId]
    }

    fun relaySend(
        toPeerId: String,
        messageId: String,
        fromPeerId: String = "UNKNOWN",
        payload: String = ""
    ): EndpointResult {
        val res = endpoint("/relay/send")
        if (res.ok && !allMessages.containsKey(messageId)) {
            val envelope =
                MessageEnvelope(
                    messageId = messageId,
                    fromPeerId = fromPeerId,
                    toPeerId = toPeerId,
                    payload = payload,
                    createdAtMs = clock.nowMs
                )
            allMessages[messageId] = envelope
            pendingByPeer.getOrPut(toPeerId) { mutableListOf() }.add(envelope)
        }
        return res
    }

    fun relayInbox(peerId: String): Pair<EndpointResult, List<String>> {
        val res = endpoint("/relay/inbox")
        val payload = if (res.ok) pendingByPeer[peerId]?.map { it.messageId }.orEmpty() else emptyList()
        return res to payload
    }

    fun relayInboxDetailed(peerId: String): Pair<EndpointResult, List<MessageEnvelope>> {
        val res = endpoint("/relay/inbox")
        val payload = if (res.ok) pendingByPeer[peerId]?.map { it.copy() }.orEmpty() else emptyList()
        return res to payload
    }

    fun ackDelivered(messageId: String, peerId: String? = null): EndpointResult {
        val res = endpoint("/receipt/delivered")
        val envelope = allMessages[messageId]
        if (res.ok && envelope != null && (peerId == null || envelope.toPeerId == peerId)) {
            envelope.delivered = true
            delivered += messageId
        }
        return res
    }

    fun ackRead(messageId: String, peerId: String? = null): EndpointResult {
        val res = endpoint("/receipt/read")
        val envelope = allMessages[messageId]
        if (res.ok && envelope != null && envelope.delivered && (peerId == null || envelope.toPeerId == peerId)) {
            envelope.read = true
            read += messageId
        }
        return res
    }

    fun startCall(callId: String, fromPeerId: String = "A", toPeerId: String = "B"): EndpointResult {
        val res = endpoint("/session/start")
        if (res.ok) {
            callState[callId] =
                CallSessionSnapshot(
                    callId = callId,
                    fromPeerId = fromPeerId,
                    toPeerId = toPeerId,
                    status = "RINGING",
                    startedAtMs = clock.nowMs
                )
        }
        return res
    }

    fun ringing(callId: String): EndpointResult {
        val res = endpoint("/session/ringing")
        if (res.ok) {
            callState[callId]?.status = "RINGING"
        }
        return res
    }

    fun acceptCall(callId: String): EndpointResult {
        val res = endpoint("/session/accept")
        if (res.ok) callState[callId]?.status = "ACCEPTED"
        return res
    }

    fun rejectCall(callId: String): EndpointResult {
        val res = endpoint("/session/reject")
        if (res.ok) callState[callId]?.status = "REJECTED"
        return res
    }

    fun endCall(callId: String): EndpointResult {
        val res = endpoint("/session/end")
        if (res.ok) callState[callId]?.status = "ENDED"
        return res
    }

    fun sendTone(callId: String, fromPeerId: String, hz: Int, durationMs: Long = 400L): EndpointResult {
        val res = endpoint("/session/tone")
        val session = callState[callId]
        if (res.ok && session != null && (session.status == "ACCEPTED" || session.status == "CONNECTED")) {
            val target = if (session.fromPeerId == fromPeerId) session.toPeerId else session.fromPeerId
            session.status = "CONNECTED"
            session.tones += ToneFrame(fromPeerId, target, hz, durationMs, clock.nowMs)
        }
        return res
    }

    fun fetchToneInbox(peerId: String, callId: String): Pair<EndpointResult, List<ToneFrame>> {
        val res = endpoint("/session/toneInbox")
        val session = callState[callId]
        val tones = session?.tones?.filter { it.toPeerId == peerId }.orEmpty()
        return res to tones
    }

    fun isDelivered(messageId: String): Boolean = delivered.contains(messageId)
    fun isRead(messageId: String): Boolean = read.contains(messageId)
    fun callStatus(callId: String): String? = callState[callId]?.status
    fun messageEnvelope(messageId: String): MessageEnvelope? = allMessages[messageId]?.copy()
    fun pendingCount(peerId: String): Int = pendingByPeer[peerId]?.size ?: 0

    private fun endpoint(path: String): EndpointResult {
        val started = clock.nowMs
        if (!net.internetAvailable) {
            return EndpointResult(path, false, 0, 0, "NO_INTERNET")
        }
        if (!net.relayAvailable && path.startsWith("/relay")) {
            return EndpointResult(path, false, 503, 10, "RELAY_DOWN")
        }
        net.serverErrorCode?.let {
            return EndpointResult(path, false, it, 20, "SERVER_ERROR_$it")
        }
        val latency = if (net.serverSlow) 1500L else 50L
        clock.advance(latency)
        return EndpointResult(path, true, 200, clock.nowMs - started)
    }
}
