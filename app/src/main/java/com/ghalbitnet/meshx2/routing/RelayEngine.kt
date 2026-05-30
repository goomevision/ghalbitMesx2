package com.ghalbitnet.meshx2.routing

import android.util.Log
import com.ghalbitnet.meshx2.economy.ServicePathRecorder
import com.ghalbitnet.meshx2.economy.UsageSessionRecorder
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.network.MeshSocketClient
import com.ghalbitnet.meshx2.reputation.ReputationManager
import com.ghalbitnet.meshx2.stats.MeshStatistics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object RelayEngine {
    private const val TAG = "GHALBIT-STORE-FORWARD"
    private const val CACHE_WINDOW_MS = 30_000L
    private const val CUSTODY_TTL_MS = 15 * 60_000L
    private const val MAX_CUSTODY_PACKETS = 300
    private val packetCache = ConcurrentHashMap<String, Long>()
    private val custodyQueue = ConcurrentHashMap<String, CustodyPacket>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class CustodyPacket(
        val packet: MeshPacket,
        val createdAt: Long,
        val lastAttemptAt: Long = 0L,
        val attempt: Int = 0,
        val reason: String
    )

    private val cleanupJob = scope.launch {
        while (isActive) {
            delay(60_000L)
            val now = System.currentTimeMillis()
            packetCache.entries.removeIf { now - it.value > CACHE_WINDOW_MS }
            val expired = custodyQueue.entries.removeIf { now - it.value.createdAt > CUSTODY_TTL_MS }
            if (expired) {
                Log.w(TAG, "expired cleanup size=${custodyQueue.size}")
            }
        }
    }

    private val flushJob = scope.launch {
        while (isActive) {
            delay(5_000L)
            flushCustody("periodic")
        }
    }

    fun relayPacket(packet: MeshPacket) {
        if (packetCache.containsKey(packet.packetId)) {
            Log.d(TAG, "duplicate ignored packetId=${packet.packetId}")
            return
        }
        if (packet.hopCount >= packet.maxHop) {
            Log.w(TAG, "maxHop reached packetId=${packet.packetId} type=${packet.type}")
            return
        }
        if (!PacketTtlManager.canForward(packet)) {
            Log.w(TAG, "ttl blocked packetId=${packet.packetId} type=${packet.type}")
            return
        }

        packetCache[packet.packetId] = System.currentTimeMillis()
        val nextPayload = PacketTtlManager.decreasePayloadTtl(packet.payload)
        if (nextPayload == null) {
            storeForForward(packet, "ttlPayloadMissing")
            return
        }
        val next = packet.copy(payload = nextPayload, hopCount = packet.hopCount + 1)
        scope.launch { forwardNowOrStore(next, "relay") }
    }

    fun flushCustody(reason: String) {
        if (custodyQueue.isEmpty()) return
        val now = System.currentTimeMillis()
        val snapshot = custodyQueue.values.sortedBy { it.createdAt }.take(40)
        Log.d(TAG, "flush reason=$reason candidates=${snapshot.size} total=${custodyQueue.size}")
        snapshot.forEach { item ->
            if (now - item.lastAttemptAt < backoffMs(item.attempt)) return@forEach
            scope.launch { forwardCustody(item, reason) }
        }
    }

    private suspend fun forwardNowOrStore(packet: MeshPacket, reason: String) {
        val directRoute = RouteDiscovery.getBestRoute(packet.destination)
        if (directRoute != null) {
            val ok = MeshSocketClient.sendBlocking(directRoute.nextHopIp, packet)
            recordForwardResult(packet, directRoute.nextHopIp, ok)
            if (ok) {
                custodyQueue.remove(packet.packetId)
                Log.d(TAG, "forwardedDirect packetId=${packet.packetId} dest=${packet.destination} via=${directRoute.nextHopIp}")
                return
            }
            storeForForward(packet, "directSendFailed")
            return
        }

        val onlineNodes = MeshRegistry.getNodes()
            .filter { it.online }
            .filter { it.name != packet.source }
        if (onlineNodes.isEmpty()) {
            storeForForward(packet, "noOnlineNodes")
            return
        }

        var deliveredToAny = false
        onlineNodes.forEach { node ->
            val ok = MeshSocketClient.sendBlocking(node.ipAddress, packet)
            recordForwardResult(packet, node.ipAddress, ok)
            deliveredToAny = deliveredToAny || ok
        }
        if (deliveredToAny) {
            custodyQueue.remove(packet.packetId)
            Log.d(TAG, "flooded packetId=${packet.packetId} dest=${packet.destination} peers=${onlineNodes.size}")
        } else {
            storeForForward(packet, "floodFailed")
        }
    }

    private suspend fun forwardCustody(item: CustodyPacket, reason: String) {
        val packet = item.packet
        val updated = item.copy(lastAttemptAt = System.currentTimeMillis(), attempt = item.attempt + 1)
        custodyQueue[packet.packetId] = updated
        Log.d(TAG, "custodyRetry packetId=${packet.packetId} attempt=${updated.attempt} reason=$reason original=${item.reason}")
        forwardNowOrStore(packet, "custodyRetry")
    }

    private fun storeForForward(packet: MeshPacket, reason: String) {
        if (packet.type == "CALL_AUDIO_FRAME") {
            Log.d(TAG, "skipRealtimeAudioCustody packetId=${packet.packetId} reason=$reason")
            return
        }
        val now = System.currentTimeMillis()
        if (custodyQueue.size >= MAX_CUSTODY_PACKETS) {
            val oldest = custodyQueue.values.minByOrNull { it.createdAt }
            if (oldest != null) {
                custodyQueue.remove(oldest.packet.packetId)
                Log.w(TAG, "evictOldest packetId=${oldest.packet.packetId}")
            }
        }
        custodyQueue.putIfAbsent(packet.packetId, CustodyPacket(packet = packet, createdAt = now, reason = reason))
        Log.d(TAG, "stored packetId=${packet.packetId} type=${packet.type} dest=${packet.destination} reason=$reason size=${custodyQueue.size}")
    }

    private fun recordForwardResult(packet: MeshPacket, nextHopIp: String, ok: Boolean) {
        if (ok) {
            ServicePathRecorder.recordRelay(packet, nextHopIp)
            UsageSessionRecorder.recordRelay(packet)
            MeshStatistics.forwardedPacket(packet.type)
        }
        ReputationManager.updateReputation(nextHopIp, ok, 0)
        Log.d(TAG, "forwardResult packetId=${packet.packetId} type=${packet.type} nextHop=$nextHopIp ok=$ok")
    }

    private fun backoffMs(attempt: Int): Long = when {
        attempt <= 0 -> 0L
        attempt == 1 -> 3_000L
        attempt == 2 -> 8_000L
        attempt == 3 -> 15_000L
        else -> 30_000L
    }
}
