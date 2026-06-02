package com.ghalbitnet.meshx2.simulation

data class VirtualPeerSyncResult(
    val fetchedMessages: Int,
    val deliveredAcks: Int,
    val readAcks: Int,
    val replyMessageIds: List<String>,
    val acceptedCalls: Int,
    val rejectedCalls: Int,
    val toneReplies: Int
)

class VirtualPeerB(
    private val peer: FakePeer,
    private val server: FakeOperatorServer,
    private val clock: FakeClock
) {
    fun syncMessagesAndCalls(activeCallId: String? = null): VirtualPeerSyncResult {
        if (!peer.online) {
            return VirtualPeerSyncResult(0, 0, 0, emptyList(), 0, 0, 0)
        }
        server.heartbeat(peer.peerId, online = true)
        val (_, inbox) = server.relayInboxDetailed(peer.peerId)
        var deliveredAcks = 0
        var readAcks = 0
        val replyIds = mutableListOf<String>()
        inbox.forEach { envelope ->
            if (server.ackDelivered(envelope.messageId, peer.peerId).ok) {
                deliveredAcks++
            }
            if (peer.autoReadMessages && server.ackRead(envelope.messageId, peer.peerId).ok) {
                readAcks++
            }
            peer.autoReplyPayload?.takeIf { it.isNotBlank() }?.let { reply ->
                val replyId = "reply-${peer.peerId}-${envelope.messageId}"
                if (server.relaySend(envelope.fromPeerId, replyId, fromPeerId = peer.peerId, payload = reply).ok) {
                    replyIds += replyId
                }
            }
        }

        var accepted = 0
        var rejected = 0
        var toneReplies = 0
        activeCallId?.let { callId ->
            when (server.callStatus(callId)) {
                "RINGING" -> {
                    if (peer.autoAcceptCalls) {
                        if (server.acceptCall(callId).ok) accepted++
                    } else {
                        if (server.rejectCall(callId).ok) rejected++
                    }
                    Unit
                }
                "CONNECTED", "ACCEPTED" -> {
                    peer.toneHz?.let { hz ->
                        val (_, tones) = server.fetchToneInbox(peer.peerId, callId)
                        if (tones.isNotEmpty() && server.sendTone(callId, peer.peerId, hz).ok) {
                            toneReplies++
                        }
                    }
                    Unit
                }
                else -> Unit
            }
        }

        return VirtualPeerSyncResult(
            fetchedMessages = inbox.size,
            deliveredAcks = deliveredAcks,
            readAcks = readAcks,
            replyMessageIds = replyIds,
            acceptedCalls = accepted,
            rejectedCalls = rejected,
            toneReplies = toneReplies
        )
    }
}
