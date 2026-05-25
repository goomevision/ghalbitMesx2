package com.ghalbitnet.meshx2.routing

import android.util.Log
import com.ghalbitnet.meshx2.economy.ServicePathRecorder
import com.ghalbitnet.meshx2.economy.UsageSessionRecorder
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.network.MeshSocketClient
import com.ghalbitnet.meshx2.reputation.ReputationManager
import com.ghalbitnet.meshx2.stats.MeshStatistics
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object RelayEngine {
    private val packetCache = ConcurrentHashMap<String, Long>() // packetId -> timestamp
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cleanupJob = scope.launch {
        while (isActive) {
            delay(60000)
            val now = System.currentTimeMillis()
            packetCache.entries.removeIf { now - it.value > 30000 }
        }
    }

    fun relayPacket(packet: MeshPacket) {
        if (packetCache.containsKey(packet.packetId) || packet.hopCount >= packet.maxHop) return
        if (!PacketTtlManager.canForward(packet)) return

        packetCache[packet.packetId] = System.currentTimeMillis()

        val nextPayload =
            PacketTtlManager.decreasePayloadTtl(packet.payload) ?: return

        val next =
            packet.copy(
                payload = nextPayload,
                hopCount = packet.hopCount + 1
            )

        scope.launch {
            val route = RouteDiscovery.getBestRoute(packet.destination)
            if (route != null) {
                MeshSocketClient.send(route.nextHopIp, next)
                ServicePathRecorder.recordRelay(next, route.nextHopIp)
                UsageSessionRecorder.recordRelay(next)
                MeshStatistics.forwardedPacket(packet.type)
                // Update reputasi jika berhasil (asumsikan berhasil)
                ReputationManager.updateReputation(route.nextHopIp, true, 0)
            } else {
                // Flood ke semua node
                MeshRegistry.getNodes()
                    .filter { it.online }
                    .filter { it.name != packet.source }
                    .forEach { node ->
                        MeshSocketClient.send(node.ipAddress, next)
                        ServicePathRecorder.recordRelay(next, node.ipAddress)
                        UsageSessionRecorder.recordRelay(next)
                        MeshStatistics.forwardedPacket(packet.type)
                        ReputationManager.updateReputation(node.ipAddress, true, 0)
                    }
            }
        }
    }
}
