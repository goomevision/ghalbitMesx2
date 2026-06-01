package com.ghalbitnet.meshx2.diagnostics

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.model.MeshNode
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.network.MeshSocketClient
import java.util.concurrent.ConcurrentHashMap

object NetworkTruthProbe {
    private const val TYPE_PING = "NET_TRUTH_PING"
    private const val TYPE_PONG = "NET_TRUTH_PONG"
    private const val PROBE_INTERVAL_MS = 15_000L
    private const val RESULT_TIMEOUT_MS = 8_000L

    private data class ProbeTicket(
        val packetId: String,
        val sourcePeerId: String,
        val targetPeerId: String,
        val transport: String,
        val sentAt: Long
    )

    private val inflight = ConcurrentHashMap<String, ProbeTicket>()
    @Volatile
    private var lastProbeAt: Long = 0L

    fun maybeSend(context: Context, localPeerId: String, candidate: MeshNode?) {
        val target = candidate ?: return
        if (!target.online) return
        val now = System.currentTimeMillis()
        if (now - lastProbeAt < PROBE_INTERVAL_MS) return
        lastProbeAt = now
        sendPing(context, localPeerId, target, now)
        flushTimeout(now)
    }

    private fun sendPing(context: Context, localPeerId: String, target: MeshNode, now: Long) {
        val packetId = "NTP-$now"
        val payload = "$now|$localPeerId|${target.name}|TCP"
        val packet = MeshPacket(
            packetId = packetId,
            source = localPeerId,
            destination = target.name,
            type = TYPE_PING,
            payload = payload,
            encrypted = false
        )
        val ok = runCatching { MeshSocketClient.sendBlocking(target.ipAddress, packet) }.getOrDefault(false)
        inflight[packetId] = ProbeTicket(packetId, localPeerId, target.name, "TCP", now)
        Log.d(
            "GHALBIT-NET-TRUTH",
            "SEND packetId=$packetId from=$localPeerId to=${target.name} ip=${target.ipAddress} transport=TCP sent=$ok"
        )
        if (!ok) {
            Log.w(
                "GHALBIT-NET-TRUTH",
                "RESULT packetId=$packetId success=false reason=send_failed transport=TCP peer=${target.name}"
            )
        }
    }

    fun onIncomingPacket(localPeerId: String, packet: MeshPacket, payload: String): MeshPacket? {
        if (packet.type == TYPE_PING) {
            val parts = payload.split('|')
            val sentAt = parts.firstOrNull()?.toLongOrNull() ?: 0L
            val src = parts.getOrNull(1).orEmpty().ifBlank { packet.source }
            Log.d(
                "GHALBIT-NET-TRUTH",
                "RECEIVE packetId=${packet.packetId} from=$src to=$localPeerId transport=TCP"
            )
            val pongPayload = "$sentAt|$src|$localPeerId|TCP|${System.currentTimeMillis()}"
            Log.d("GHALBIT-NET-TRUTH", "PONG packetId=${packet.packetId} from=$localPeerId to=$src")
            return MeshPacket(
                packetId = "NTP-PONG-${System.currentTimeMillis()}",
                source = localPeerId,
                destination = packet.source,
                type = TYPE_PONG,
                payload = pongPayload,
                encrypted = false
            )
        }

        if (packet.type == TYPE_PONG) {
            val parts = payload.split('|')
            val sentAt = parts.firstOrNull()?.toLongOrNull() ?: 0L
            val txPeer = parts.getOrNull(1).orEmpty()
            val rxPeer = parts.getOrNull(2).orEmpty()
            val now = System.currentTimeMillis()
            val rtt = if (sentAt > 0L) (now - sentAt).coerceAtLeast(0L) else -1L
            val ticket = inflight.entries.firstOrNull { it.value.targetPeerId == packet.source || it.value.targetPeerId == rxPeer }
            ticket?.let { inflight.remove(it.key) }
            Log.d(
                "GHALBIT-NET-TRUTH",
                "RESULT packetId=${ticket?.value?.packetId ?: packet.packetId} success=true rttMs=$rtt transport=TCP src=$txPeer dst=$rxPeer"
            )
        }
        return null
    }

    private fun flushTimeout(now: Long) {
        val expired = inflight.values.filter { now - it.sentAt > RESULT_TIMEOUT_MS }
        if (expired.isEmpty()) return
        expired.forEach {
            inflight.remove(it.packetId)
            Log.w(
                "GHALBIT-NET-TRUTH",
                "RESULT packetId=${it.packetId} success=false reason=timeout transport=${it.transport} peer=${it.targetPeerId}"
            )
        }
    }
}

