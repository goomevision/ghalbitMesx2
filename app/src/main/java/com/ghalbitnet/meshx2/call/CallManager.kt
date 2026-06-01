package com.ghalbitnet.meshx2.call

import android.content.Context
import android.util.Base64
import android.util.Log
import com.ghalbitnet.meshx2.MainActivity
import com.ghalbitnet.meshx2.identity.CentralIdentityResolver
import com.ghalbitnet.meshx2.chat.AdaptiveRouteManager
import com.ghalbitnet.meshx2.chat.RouteEvidenceSource
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.network.MeshSocketClient
import com.ghalbitnet.meshx2.network.ReliablePacketSender
import com.ghalbitnet.meshx2.routing.IntelligentRouteMemory
import com.ghalbitnet.meshx2.routing.RouteDiscovery
import com.ghalbitnet.meshx2.routing.RouteHint
import com.ghalbitnet.meshx2.security.KeyStoreManager
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

object CallManager {
    const val SIGNAL_CALL_START = "CALL_START"
    const val SIGNAL_CALL_INVITE = "CALL_INVITE"
    const val SIGNAL_CALL_ACCEPT = "CALL_ACCEPT"
    const val SIGNAL_CALL_REJECT = "CALL_REJECT"
    const val SIGNAL_CALL_END = "CALL_END"
    const val SIGNAL_CALL_BUSY = "CALL_BUSY"
    const val SIGNAL_CALL_AUDIO_FRAME = "CALL_AUDIO_FRAME"
    const val SIGNAL_VOICE_HELLO = "VOICE_HELLO"
    const val SIGNAL_VOICE_HELLO_ACK = "VOICE_HELLO_ACK"
    const val SIGNAL_VOICE_TRANSPORT_PROBE = "VOICE_TRANSPORT_PROBE"
    const val SIGNAL_VOICE_TRANSPORT_ACK = "VOICE_TRANSPORT_ACK"
    const val SIGNAL_VOICE_STREAM_START = "VOICE_STREAM_START"
    const val SIGNAL_VOICE_STREAM_ACTIVE_ACK = "VOICE_STREAM_ACTIVE_ACK"
    const val SIGNAL_VOICE_HEARTBEAT = "VOICE_HEARTBEAT"
    const val SIGNAL_VOICE_STREAM_END = "VOICE_STREAM_END"
    const val SIGNAL_VOICE_PROBE = "VOICE_PROBE"
    const val SIGNAL_VOICE_PROBE_ACK = "VOICE_PROBE_ACK"
    const val SIGNAL_CALL_WEBRTC_OFFER = "CALL_WEBRTC_OFFER"
    const val SIGNAL_CALL_WEBRTC_ANSWER = "CALL_WEBRTC_ANSWER"
    const val SIGNAL_CALL_WEBRTC_ICE = "CALL_WEBRTC_ICE"
    const val CODEC_PCM16_8K = "PCM16_8K"
    private val audioTxCounter = AtomicInteger(0)
    private val audioRxCounter = AtomicInteger(0)
    private val audioParseFailCounter = AtomicInteger(0)

    fun publicKeyHash(publicKey: String?): String? {
        if (publicKey.isNullOrBlank()) return null
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKey.toByteArray())
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    fun resolvePeer(
        context: Context,
        peerName: String,
        ipHint: String? = null,
        globalIdHint: String? = null,
        publicKeyHint: String? = null,
        walletAddressHint: String? = null,
        displayNameHint: String? = null
    ): CallPeerEndpoint {
        val resolved =
            CentralIdentityResolver.resolve(
                context = context,
                legacyChatId = peerName,
                peerName = peerName,
                peerIp = ipHint,
                globalIdHint = globalIdHint,
                publicKeyHint = publicKeyHint,
                walletAddressHint = walletAddressHint,
                displayNameHint = displayNameHint ?: peerName
            )

        val routeByGlobal =
            resolved.globalId?.let { IntelligentRouteMemory.getHint(context, it) }
        val routeByNode =
            IntelligentRouteMemory.getHint(context, peerName)
        val transportIp =
            routeByGlobal?.nextHopId
                ?: routeByNode?.nextHopId
                ?: resolved.peerIp.ifBlank { null }
                ?: ipHint

        return CallPeerEndpoint(
            nodeId = peerName,
            globalId = resolved.globalId,
            publicKey = resolved.publicKey,
            publicKeyHash = publicKeyHash(resolved.publicKey ?: publicKeyHint),
            walletAddress = resolved.walletAddress,
            displayName = resolved.displayName,
            routeHint = transportIp,
            transportIp = transportIp
        )
    }

    fun isSelfCall(
        localNodeId: String,
        localGlobalId: String?,
        localPublicKeyHash: String?,
        peer: CallPeerEndpoint
    ): Boolean {
        return peer.nodeId == localNodeId ||
            (!localGlobalId.isNullOrBlank() && localGlobalId == peer.globalId) ||
            (!localPublicKeyHash.isNullOrBlank() && localPublicKeyHash == peer.publicKeyHash)
    }

    fun rememberRoute(
        context: Context,
        peer: CallPeerEndpoint,
        sourceIp: String?
    ) {
        val nextHop = sourceIp?.takeIf { it.isNotBlank() } ?: peer.transportIp ?: return
        val ids =
            listOfNotNull(peer.nodeId, peer.globalId, peer.publicKeyHash).distinct()
        ids.forEach { destinationId ->
            IntelligentRouteMemory.rememberHint(
                context,
                RouteHint(
                    destinationId = destinationId,
                    nextHopId = nextHop,
                    latencyMs = 0L,
                    hopCount = 1,
                    trustScore = 60,
                    lastSeen = System.currentTimeMillis()
                )
            )
        }
        RouteDiscovery.rememberDirectRoute(peer.nodeId, nextHop, trustScore = 60)
    }

    fun buildSignalPayload(
        callId: String,
        localNodeId: String,
        localGlobalId: String?,
        localPublicKeyHash: String?
    ): String {
        return JSONObject()
            .put("callId", callId)
            .put("peerName", localNodeId)
            .put("sourceNodeId", localNodeId)
            .put("sourceGlobalId", localGlobalId)
            .put("sourcePublicKeyHash", localPublicKeyHash)
            .toString()
    }

    suspend fun sendSignal(
        context: Context,
        peer: CallPeerEndpoint,
        type: String,
        callId: String,
        localNodeId: String,
        localGlobalId: String?,
        localPublicKeyHash: String?,
        retryCount: Int = 3
    ): Boolean {
        val lockedRoute = AdaptiveRouteManager.resolveLockedNextHop(peer.nodeId, peer.globalId)
        val targetIp = lockedRoute ?: peer.routeHint ?: peer.transportIp
        if (targetIp.isNullOrBlank()) {
            Log.w("GHALBIT-CALL-SIGNAL", "missing route for ${peer.nodeId} type=$type")
            return false
        }

        val packet =
            MeshPacket(
                packetId = "$type-${System.currentTimeMillis()}",
                source = localNodeId,
                destination = peer.nodeId,
                type = type,
                payload = buildSignalPayload(callId, localNodeId, localGlobalId, localPublicKeyHash),
                encrypted = false
            )

        val sent =
            ReliablePacketSender.sendWithRetry(
                ipAddress = targetIp,
                packet = packet,
                retryCount = retryCount,
                delayMs = 700L
            )
        Log.d("GHALBIT-CALL-SIGNAL", "type=$type peer=${peer.nodeId} ip=$targetIp sent=$sent")
        return sent
    }

    suspend fun sendCustomSignal(
        context: Context,
        peer: CallPeerEndpoint,
        type: String,
        payload: String,
        localNodeId: String,
        retryCount: Int = 2
    ): Boolean {
        val lockedRoute = AdaptiveRouteManager.resolveLockedNextHop(peer.nodeId, peer.globalId)
        val targetIp = lockedRoute ?: peer.routeHint ?: peer.transportIp
        if (targetIp.isNullOrBlank()) {
            Log.w("GHALBIT-VOIP-SIGNAL", "missing route for ${peer.nodeId} type=$type")
            return false
        }

        val packet =
            MeshPacket(
                packetId = "$type-${System.currentTimeMillis()}",
                source = localNodeId,
                destination = peer.nodeId,
                type = type,
                payload = payload,
                encrypted = false
            )

        val sent =
            ReliablePacketSender.sendWithRetry(
                ipAddress = targetIp,
                packet = packet,
                retryCount = retryCount,
                delayMs = 450L
            )
        Log.d("GHALBIT-VOIP-SIGNAL", "type=$type peer=${peer.nodeId} ip=$targetIp sent=$sent")
        return sent
    }

    fun sendAudioFrame(
        peer: CallPeerEndpoint,
        callId: String,
        localNodeId: String,
        sequenceNumber: Int,
        audioData: ByteArray
    ): Boolean {
        val voicePacket =
            VoicePacket(
                sessionId = callId,
                senderId = localNodeId,
                sequence = sequenceNumber,
                timestamp = System.currentTimeMillis(),
                mode = AdaptiveVoiceMode.LIVE_VOICE,
                payload = audioData,
                priority = VoicePacketPriority.HIGH
            )
        return sendVoicePacket(peer, localNodeId, voicePacket)
    }

    fun sendVoicePacket(
        peer: CallPeerEndpoint,
        localNodeId: String,
        packet: VoicePacket
    ): Boolean {
        val lockedRoute = AdaptiveRouteManager.resolveLockedNextHop(peer.nodeId, peer.globalId)
        val targetIp = lockedRoute ?: peer.routeHint ?: peer.transportIp
        if (targetIp.isNullOrBlank()) {
            Log.w("GHALBIT-CALL-AUDIO-TX", "drop frame no route peer=${peer.nodeId}")
            return false
        }
        if (packet.payload.isEmpty()) {
            Log.w("GHALBIT-CALL-AUDIO-TX", "drop empty frame seq=${packet.sequence} peer=${peer.nodeId}")
            return false
        }

        val encodedAudio = Base64.encodeToString(packet.payload, Base64.NO_WRAP)
        val payload =
            JSONObject()
                .put("callId", packet.sessionId)
                .put("sourceNodeId", localNodeId)
                .put("targetNodeId", peer.nodeId)
                .put("sequenceNumber", packet.sequence)
                .put("timestamp", packet.timestamp)
                .put("codec", CODEC_PCM16_8K)
                .put("mode", packet.mode.name)
                .put("priority", packet.priority.name)
                .put("checksum", packet.checksum)
                .put("audioData", encodedAudio)
                .toString()

        val meshPacket =
            MeshPacket(
                packetId = "$SIGNAL_CALL_AUDIO_FRAME-${System.currentTimeMillis()}-${packet.sequence}",
                source = localNodeId,
                destination = peer.nodeId,
                type = SIGNAL_CALL_AUDIO_FRAME,
                payload = payload,
                encrypted = false
            )

        val sent = MeshSocketClient.sendBlocking(targetIp, meshPacket)
        Log.d("GHALBIT-CALL-ROUTE", "route=$targetIp locked=${lockedRoute != null}")
        val tx = if (sent) audioTxCounter.incrementAndGet() else audioTxCounter.get()
        Log.d("GHALBIT-VOICE-PACKET", "sent seq=${packet.sequence}")
        Log.d(
            "GHALBIT-CALL-AUDIO-TX",
            "seq=${packet.sequence} peer=${peer.nodeId} ip=$targetIp sent=$sent bytes=${packet.payload.size} encoded=${encodedAudio.length} totalTx=$tx"
        )
        if (!sent) {
            Log.w("GHALBIT-CALL-AUDIO-TX", "send failed seq=${packet.sequence} route=$targetIp")
        } else if (packet.sequence == 1 || packet.sequence % 40 == 0) {
            AdaptiveRouteManager.recordRouteEvidence(
                chatId = peer.nodeId,
                globalId = peer.globalId,
                nextHop = targetIp,
                transport = "LOCAL_MESH_DIRECT",
                source = RouteEvidenceSource.CALL,
                confidence = 88
            )
        }
        return sent
    }

    fun parseVoicePacket(payload: String): VoicePacket? {
        val json = runCatching { JSONObject(payload) }.getOrElse { error ->
            val fail = audioParseFailCounter.incrementAndGet()
            Log.w("GHALBIT-CALL-AUDIO-RX", "parse json failed count=$fail reason=${error.message}")
            return null
        }
        val sessionId = json.optString("callId")
        val sequence = json.optInt("sequenceNumber", -1)
        val encoded = json.optString("audioData")
        if (sessionId.isBlank() || sequence < 0 || encoded.isBlank()) {
            val fail = audioParseFailCounter.incrementAndGet()
            Log.w(
                "GHALBIT-CALL-AUDIO-RX",
                "parse missing fields count=$fail callIdBlank=${sessionId.isBlank()} sequence=$sequence audioBlank=${encoded.isBlank()}"
            )
            return null
        }
        val raw = runCatching { Base64.decode(encoded, Base64.NO_WRAP) }.getOrElse { error ->
            val fail = audioParseFailCounter.incrementAndGet()
            Log.w("GHALBIT-CALL-AUDIO-RX", "parse base64 failed count=$fail seq=$sequence reason=${error.message}")
            return null
        }
        if (raw.isEmpty()) {
            val fail = audioParseFailCounter.incrementAndGet()
            Log.w("GHALBIT-CALL-AUDIO-RX", "parse empty audio count=$fail seq=$sequence")
            return null
        }
        val mode =
            AdaptiveVoiceMode.entries.firstOrNull { it.name == json.optString("mode") }
                ?: AdaptiveVoiceMode.LIVE_VOICE
        val priority =
            VoicePacketPriority.entries.firstOrNull { it.name == json.optString("priority") }
                ?: VoicePacketPriority.NORMAL
        val rx = audioRxCounter.get()
        Log.d("GHALBIT-CALL-AUDIO-RX", "parsed seq=$sequence bytes=${raw.size} totalRx=$rx")
        return VoicePacket(
            sessionId = sessionId,
            senderId = json.optString("sourceNodeId"),
            sequence = sequence,
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            mode = mode,
            payload = raw,
            priority = priority,
            checksum = json.optInt("checksum", raw.contentHashCode())
        )
    }

    fun buildVoiceAck(sessionId: String, lastReceivedSequence: Int, missingSequences: List<Int>): VoiceAck {
        return VoiceAck(
            sessionId = sessionId,
            lastReceivedSequence = lastReceivedSequence,
            missingSequences = missingSequences,
            timestamp = System.currentTimeMillis()
        )
    }

    fun recordAudioFrameReceived() {
        val rx = audioRxCounter.incrementAndGet()
        if (rx == 1 || rx % 50 == 0) {
            Log.d("GHALBIT-CALL-AUDIO-RX", "frameReceived totalRx=$rx")
        }
    }

    fun audioTxCount(): Int = audioTxCounter.get()

    fun audioRxCount(): Int = audioRxCounter.get()

    fun audioParseFailCount(): Int = audioParseFailCounter.get()

    fun extractCallId(payload: String): String? =
        runCatching { JSONObject(payload).optString("callId") }.getOrNull()

    fun extractSourceNodeId(payload: String): String? =
        runCatching { JSONObject(payload).optString("sourceNodeId") }.getOrNull()

    fun extractSourceGlobalId(payload: String): String? =
        runCatching { JSONObject(payload).optString("sourceGlobalId") }.getOrNull()

    fun extractSourcePublicKeyHash(payload: String): String? =
        runCatching { JSONObject(payload).optString("sourcePublicKeyHash") }.getOrNull()

    fun localPublicKeyHash(context: Context): String? {
        return publicKeyHash(KeyStoreManager(context).publicKeyBase64)
    }
}
