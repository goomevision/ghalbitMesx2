package com.ghalbitnet.meshx2.core.network

import android.content.Context
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.economy.InternetGatewayLoadManager
import com.ghalbitnet.meshx2.economy.MeshServiceLedger
import com.ghalbitnet.meshx2.model.MeshNode

object InternetGatewayRegistry {

    data class GatewaySelection(
        val nodeId: String,
        val name: String,
        val ipAddress: String,
        val isLocal: Boolean,
        val modeLabel: String,
        val trusted: Int,
        val latency: Int,
        val signal: Int,
        val activeLoad: Int,
        val recentUsageMb: Double,
        val routeScore: Int,
        val routeReason: String
    ) {
        fun summary(context: Context): String {
            return if (isLocal) {
                context.getString(R.string.gateway_active_local) + " | skor $routeScore"
            } else {
                context.getString(
                    R.string.gateway_active_remote,
                    name,
                    ipAddress
                ) + " | skor $routeScore"
            }
        }
    }

    fun candidates(
        context: Context,
        nodes: List<MeshNode>
    ): List<GatewaySelection> {
        val items = mutableListOf<GatewaySelection>()
        val selfInternetReady =
            ConnectivityStatusDetector.snapshot(context, emptyList()).hasInternet

        if (selfInternetReady) {
            items += GatewaySelection(
                nodeId = "local",
                name = context.getString(R.string.gateway_this_device),
                ipAddress = "local",
                isLocal = true,
                modeLabel = context.getString(R.string.transport_lan_hotspot),
                trusted = 100,
                latency = 12,
                signal = 100,
                activeLoad = InternetGatewayLoadManager.activeLoad(context, "local"),
                recentUsageMb = MeshServiceLedger.recentGatewayUsageMb(context, "local"),
                routeScore = 92,
                routeReason = "jalur internet langsung perangkat ini"
            )
        }

        items +=
            nodes
                .filter { it.online && it.gateway }
                .map { node ->
                    val trustPart = node.trusted.coerceIn(0, 100) * 0.40
                    val latencyPart = latencyScore(node.latency) * 0.25
                    val signalPart = node.signal.coerceIn(0, 100) * 0.20
                    val stabilityPart = if (node.online) 10.0 else 0.0
                    val gatewayId = node.publicKey.ifBlank { node.name }
                    val activeLoad = InternetGatewayLoadManager.activeLoad(context, gatewayId)
                    val recentUsageMb = MeshServiceLedger.recentGatewayUsageMb(context, gatewayId)
                    val loadPenalty = activeLoad * 7.5
                    val recentUsagePenalty = (recentUsageMb / 48.0).coerceAtMost(12.0)
                    val routeModeBonus =
                        when (TransportPreference.modeForAddress(node.ipAddress)) {
                            TransportPreference.Mode.LAN_HOTSPOT -> 5.0
                            TransportPreference.Mode.NEARBY -> 2.0
                            else -> 0.0
                        }
                    val routeScore =
                        (
                            trustPart +
                                latencyPart +
                                signalPart +
                                stabilityPart +
                                routeModeBonus -
                                loadPenalty -
                                recentUsagePenalty
                            )
                            .toInt()
                            .coerceIn(0, 100)

                    GatewaySelection(
                        nodeId = gatewayId,
                        name = node.name,
                        ipAddress = node.ipAddress,
                        isLocal = false,
                        modeLabel = TransportPreference.modeForAddress(node.ipAddress).label,
                        trusted = node.trusted,
                        latency = node.latency,
                        signal = node.signal,
                        activeLoad = activeLoad,
                        recentUsageMb = recentUsageMb,
                        routeScore = routeScore,
                        routeReason =
                            "trust ${node.trusted.coerceIn(0, 100)} | " +
                                "latency ${normalizedLatency(node.latency)} ms | " +
                                "sinyal ${node.signal.coerceIn(0, 100)}% | " +
                                "beban $activeLoad | " +
                                "riwayat ${"%.1f".format(recentUsageMb)} MB"
                    )
                }

        return items
            .sortedWith(
                compareByDescending<GatewaySelection> { it.routeScore }
                    .thenBy { normalizedLatency(it.latency) }
                    .thenByDescending { it.signal }
                    .thenByDescending { it.trusted }
            )
    }

    fun select(
        context: Context,
        nodes: List<MeshNode>
    ): GatewaySelection? {
        return candidates(context, nodes).firstOrNull()
    }

    fun summaryText(
        context: Context,
        nodes: List<MeshNode>
    ): String {
        return select(context, nodes)?.summary(context)
            ?: context.getString(R.string.gateway_active_none)
    }

    private fun latencyScore(latency: Int): Int {
        val normalized = normalizedLatency(latency)
        return when {
            normalized <= 25 -> 100
            normalized <= 60 -> 92
            normalized <= 100 -> 82
            normalized <= 180 -> 68
            normalized <= 300 -> 52
            else -> 35
        }
    }

    private fun normalizedLatency(latency: Int): Int {
        return if (latency >= 0) latency else Int.MAX_VALUE
    }
}
