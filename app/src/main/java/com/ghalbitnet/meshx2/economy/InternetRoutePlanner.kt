package com.ghalbitnet.meshx2.economy

import android.content.Context
import com.ghalbitnet.meshx2.core.network.InternetGatewayRegistry
import com.ghalbitnet.meshx2.model.MeshNode
import kotlin.math.roundToInt

object InternetRoutePlanner {

    data class RoutePlan(
        val gateway: InternetGatewayRegistry.GatewaySelection,
        val relayPath: List<ServiceParticipant>,
        val routeScore: Int,
        val routeReason: String
    ) {
        val routeKey: String
            get() =
                buildString {
                    append(gateway.nodeId)
                    append('|')
                    append(relayPath.joinToString(">") { it.nodeId.ifBlank { it.nodeName } })
                }
    }

    fun plan(
        context: Context,
        nodes: List<MeshNode>
    ): RoutePlan? {
        return plans(context, nodes)
            .sortedByDescending { it.routeScore }
            .firstOrNull()
    }

    fun plans(
        context: Context,
        nodes: List<MeshNode>
    ): List<RoutePlan> {
        return enumeratePlans(context, nodes)
            .distinctBy { "${it.gateway.nodeId}|${it.routeReason}" }
            .sortedByDescending { it.routeScore }
    }

    fun backupSummary(
        context: Context,
        nodes: List<MeshNode>,
        selected: RoutePlan?
    ): String {
        val plans =
            plans(context, nodes)
                .filterNot { it.gateway.nodeId == selected?.gateway?.nodeId && it.routeReason == selected?.routeReason }
                .take(2)

        return plans.joinToString(", ") {
            "${it.gateway.name} (${it.routeScore})"
        }
    }

    private fun enumeratePlans(
        context: Context,
        nodes: List<MeshNode>
    ): List<RoutePlan> {
        val gatewayCandidates =
            InternetGatewayRegistry.candidates(context, nodes)
        if (gatewayCandidates.isEmpty()) {
            return emptyList()
        }

        val relayCandidates =
            nodes
                .filter { it.online && it.relay }
                .sortedByDescending { relayScore(it) }
                .take(4)

        return gatewayCandidates.flatMap { gateway ->
            val routes = mutableListOf<RoutePlan>()

            routes +=
                RoutePlan(
                    gateway = gateway,
                    relayPath = emptyList(),
                    routeScore = gateway.routeScore.coerceIn(0, 100),
                    routeReason = "langsung ke ${gateway.name}"
                )

            relayCandidates
                .filterNot { it.ipAddress == gateway.ipAddress || it.name == gateway.name }
                .forEach { relay ->
                    val relayParticipant =
                        ServiceParticipant(
                            nodeId = relay.publicKey.ifBlank { relay.name },
                            nodeName = relay.name,
                            nodeAddress = relay.ipAddress,
                            role = ServiceRole.RELAY,
                            local = false,
                            trustScore = relay.trusted.coerceIn(10, 100)
                        )

                    val oneHopScore =
                        (
                            gateway.routeScore * 0.72 +
                                relayScore(relay) * 0.28 -
                                6.0
                            ).roundToInt().coerceIn(0, 100)

                    routes +=
                        RoutePlan(
                            gateway = gateway,
                            relayPath = listOf(relayParticipant),
                            routeScore = oneHopScore,
                            routeReason = "${relay.name} -> ${gateway.name}"
                        )
                }

            if (relayCandidates.size >= 2) {
                val first =
                    relayCandidates.firstOrNull { it.ipAddress != gateway.ipAddress }
                val second =
                    relayCandidates.firstOrNull {
                        it.ipAddress != gateway.ipAddress &&
                            it.ipAddress != first?.ipAddress
                    }

                if (first != null && second != null) {
                    val path =
                        listOf(first, second).map { relay ->
                            ServiceParticipant(
                                nodeId = relay.publicKey.ifBlank { relay.name },
                                nodeName = relay.name,
                                nodeAddress = relay.ipAddress,
                                role = ServiceRole.RELAY,
                                local = false,
                                trustScore = relay.trusted.coerceIn(10, 100)
                            )
                        }
                    val twoHopScore =
                        (
                            gateway.routeScore * 0.60 +
                                relayScore(first) * 0.22 +
                                relayScore(second) * 0.18 -
                                12.0
                            ).roundToInt().coerceIn(0, 100)
                    routes +=
                        RoutePlan(
                            gateway = gateway,
                            relayPath = path,
                            routeScore = twoHopScore,
                            routeReason = "${first.name} -> ${second.name} -> ${gateway.name}"
                        )
                }
            }

            routes
        }
    }

    private fun relayScore(node: MeshNode): Double {
        val trustPart = node.trusted.coerceIn(0, 100) * 0.45
        val signalPart = node.signal.coerceIn(0, 100) * 0.20
        val latencyPart =
            when {
                node.latency <= 25 -> 100.0
                node.latency <= 60 -> 90.0
                node.latency <= 120 -> 78.0
                node.latency <= 220 -> 60.0
                else -> 42.0
            } * 0.25
        val livePart = if (node.online) 10.0 else 0.0
        return (trustPart + signalPart + latencyPart + livePart).coerceIn(0.0, 100.0)
    }
}
