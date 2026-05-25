package com.ghalbitnet.meshx2.core.manager

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.future.FutureFlags
import com.ghalbitnet.meshx2.future.ai.AiRoutingEngine
import com.ghalbitnet.meshx2.future.ai.RouteCandidate
import com.ghalbitnet.meshx2.future.diagnostic.DiagnosticCenter
import com.ghalbitnet.meshx2.future.qos.DynamicQoSManager
import com.ghalbitnet.meshx2.future.reward.RewardEngine
import com.ghalbitnet.meshx2.future.sync.OfflineSyncManager
import com.ghalbitnet.meshx2.future.sync.SyncItem
import com.ghalbitnet.meshx2.future.vpn.VpnPolicyManager
import com.ghalbitnet.meshx2.model.MeshNode
import java.util.UUID

/**
 * =========================================================
 * GHALBIT CORE MANAGER
 * =========================================================
 *
 * Manager pusat untuk menghubungkan:
 * - AI routing
 * - QoS
 * - reward
 * - offline sync
 * - diagnostic
 * - VPN policy
 *
 * Tujuan:
 * - MainActivity tetap ringan
 * - fitur masa depan mudah dinyalakan
 * - Android versi rendah tidak terbebani
 *
 * =========================================================
 */

object GhalbitCoreManager {

    private const val TAG = "GHALBIT_CORE"

    fun init(context: Context) {
        Log.d(TAG, "Core manager initialized")

        // FUTURE:
        // - load konfigurasi user
        // - load mode hemat baterai
        // - load policy node
        // - load AI model lokal ringan
    }

    /**
     * =====================================================
     * DIAGNOSTIC
     * =====================================================
     */

    fun checkPermissions(
        hasLocation: Boolean,
        hasBluetooth: Boolean,
        hasNotification: Boolean
    ): String {

        return DiagnosticCenter.permissionStatus(
            hasLocation = hasLocation,
            hasBluetooth = hasBluetooth,
            hasNotification = hasNotification
        )
    }

    fun checkNetworkHealth(
        nodeCount: Int,
        averageLatency: Long
    ): String {

        return DiagnosticCenter.networkHealth(
            nodeCount = nodeCount,
            averageLatency = averageLatency
        )
    }

    /**
     * =====================================================
     * QOS
     * =====================================================
     */

    fun getPacketPriority(
        packetType: String
    ): Int {

        if (!FutureFlags.ENABLE_DYNAMIC_QOS) {
            return 50
        }

        return DynamicQoSManager.getPriority(packetType)
    }

    fun shouldDelayPacket(
        packetType: String,
        networkBusy: Boolean
    ): Boolean {

        if (!FutureFlags.ENABLE_DYNAMIC_QOS) {
            return false
        }

        return DynamicQoSManager.shouldDelay(
            packetType,
            networkBusy
        )
    }

    /**
     * =====================================================
     * AI ROUTING
     * =====================================================
     */

    fun chooseBestNode(
        nodes: List<MeshNode>
    ): MeshNode? {

        if (nodes.isEmpty()) return null

        if (!FutureFlags.ENABLE_AI_ROUTING) {
            return nodes.firstOrNull { it.online }
        }

        val candidates =
            nodes
                .filter { it.online }
                .map {
                    RouteCandidate(
                        peerId = it.name,
                        ipAddress = it.ipAddress,
                        latencyMs = 50L,
                        trust = it.trusted,
                        batteryLevel = 80,
                        hopCount = 1
                    )
                }

        val best =
            AiRoutingEngine.chooseBestNode(candidates)

        return nodes.firstOrNull {
            it.ipAddress == best?.ipAddress
        }
    }

    /**
     * =====================================================
     * REWARD
     * =====================================================
     */

    fun calculateRelayReward(
        bytesForwarded: Long,
        success: Boolean,
        latencyMs: Long
    ): Double {

        if (!FutureFlags.ENABLE_REWARD_ENGINE) {
            return 0.0
        }

        return RewardEngine.calculateRelayReward(
            bytesForwarded = bytesForwarded,
            success = success,
            latencyMs = latencyMs
        )
    }

    /**
     * =====================================================
     * OFFLINE SYNC
     * =====================================================
     */

    fun queueOfflineData(
        type: String,
        payload: String
    ) {

        if (!FutureFlags.ENABLE_OFFLINE_SYNC) {
            return
        }

        OfflineSyncManager.add(
            SyncItem(
                id = UUID.randomUUID().toString(),
                type = type,
                payload = payload
            )
        )
    }

    fun pendingSyncCount(): Int {
        return OfflineSyncManager.count()
    }

    /**
     * =====================================================
     * VPN POLICY
     * =====================================================
     */

    fun shouldRouteThroughMesh(
        host: String
    ): Boolean {

        if (!FutureFlags.ENABLE_FULL_VPN_TUNNEL) {
            return host.endsWith(".mesh")
        }

        return VpnPolicyManager.shouldRouteThroughMesh(host)
    }

    fun isInternetForwardingAllowed(): Boolean {
        return VpnPolicyManager.isInternetForwardingAllowed()
    }
}
