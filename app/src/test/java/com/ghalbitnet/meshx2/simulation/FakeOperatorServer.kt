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

class FakeOperatorServer(
    private val clock: FakeClock,
    private val net: FakeNetworkCondition
) {
    private val registeredPeers = mutableSetOf<String>()
    private val pendingByPeer = mutableMapOf<String, MutableList<String>>()
    private val delivered = mutableSetOf<String>()
    private val read = mutableSetOf<String>()
    private val callState = mutableMapOf<String, String>()

    fun health(): EndpointResult = endpoint("/health")
    fun registerDevice(peerId: String): EndpointResult {
        val res = endpoint("/identity/register")
        if (res.ok) registeredPeers += peerId
        return res
    }

    fun heartbeat(peerId: String): EndpointResult {
        val res = endpoint("/presence/heartbeat")
        if (res.ok) registeredPeers += peerId
        return res
    }

    fun lookup(peerId: String): Pair<EndpointResult, Boolean> {
        val res = endpoint("/identity/lookup/$peerId")
        return res to registeredPeers.contains(peerId)
    }

    fun relaySend(toPeerId: String, messageId: String): EndpointResult {
        val res = endpoint("/relay/send")
        if (res.ok) pendingByPeer.getOrPut(toPeerId) { mutableListOf() }.add(messageId)
        return res
    }

    fun relayInbox(peerId: String): Pair<EndpointResult, List<String>> {
        val res = endpoint("/relay/inbox")
        val payload = if (res.ok) pendingByPeer[peerId]?.toList().orEmpty() else emptyList()
        return res to payload
    }

    fun ackDelivered(messageId: String): EndpointResult {
        val res = endpoint("/receipt/delivered")
        if (res.ok) delivered += messageId
        return res
    }

    fun ackRead(messageId: String): EndpointResult {
        val res = endpoint("/receipt/read")
        if (res.ok) read += messageId
        return res
    }

    fun startCall(callId: String): EndpointResult {
        val res = endpoint("/session/start")
        if (res.ok) callState[callId] = "RINGING"
        return res
    }

    fun acceptCall(callId: String): EndpointResult {
        val res = endpoint("/session/accept")
        if (res.ok) callState[callId] = "ACCEPTED"
        return res
    }

    fun rejectCall(callId: String): EndpointResult {
        val res = endpoint("/session/reject")
        if (res.ok) callState[callId] = "REJECTED"
        return res
    }

    fun endCall(callId: String): EndpointResult {
        val res = endpoint("/session/end")
        if (res.ok) callState[callId] = "ENDED"
        return res
    }

    fun isDelivered(messageId: String): Boolean = delivered.contains(messageId)
    fun isRead(messageId: String): Boolean = read.contains(messageId)
    fun callStatus(callId: String): String? = callState[callId]

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

