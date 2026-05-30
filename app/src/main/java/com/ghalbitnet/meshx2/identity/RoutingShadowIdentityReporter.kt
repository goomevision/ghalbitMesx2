package com.ghalbitnet.meshx2.identity

import android.content.Context
import com.ghalbitnet.meshx2.core.node.NodeStatusManager
import com.ghalbitnet.meshx2.economy.ServicePathRecorder
import com.ghalbitnet.meshx2.routing.RouteTable

object RoutingShadowIdentityReporter {

    fun inspect(
        context: Context,
        limit: Int = 40
    ): List<RoutingShadowIdentity> {
        val identities = linkedMapOf<String, RoutingShadowIdentity>()

        RouteTable.allRoutes().forEach { route ->
            val resolved =
                CentralIdentityResolver.resolve(
                    context = context,
                    legacyChatId = route.destination,
                    peerName = route.destination,
                    peerIp = route.nextHop,
                    useKeyStore = false,
                    reinforce = false
                )
            val quality =
                IdentityQualityReporter.score(resolved)

            identities[route.destination] =
                RoutingShadowIdentity(
                    routeOwnerLegacyId = route.destination,
                    routeOwnerGlobalId = resolved.globalId,
                    routeOwnerPublicKey = resolved.publicKey,
                    confidence = quality.score,
                    source = resolved.resolutionSource ?: "route-table",
                    riskLevel = riskFor(quality.label, route.destination, route.nextHop),
                    lastSeenAt = resolved.resolvedAt,
                    walletAddress = resolved.walletAddress,
                    peerIp = route.nextHop,
                    peerName = route.destination
                )
        }

        NodeStatusManager.getOnlineNodes().forEach { node ->
            val resolved =
                CentralIdentityResolver.resolve(
                    context = context,
                    legacyChatId = node.name,
                    peerName = node.name,
                    peerIp = node.ipAddress,
                    publicKeyHint = node.publicKey,
                    useKeyStore = false,
                    reinforce = false
                )
            val quality =
                IdentityQualityReporter.score(resolved)

            identities.putIfAbsent(
                node.name,
                RoutingShadowIdentity(
                    routeOwnerLegacyId = node.name,
                    routeOwnerGlobalId = resolved.globalId,
                    routeOwnerPublicKey = resolved.publicKey,
                    confidence = quality.score,
                    source = resolved.resolutionSource ?: "node-status",
                    riskLevel = riskFor(quality.label, node.name, node.ipAddress),
                    lastSeenAt = node.lastSeen,
                    walletAddress = resolved.walletAddress,
                    peerIp = node.ipAddress,
                    peerName = node.name
                )
            )
        }

        ServicePathRecorder.recentRelayParticipants(context, limit / 2).forEach { relay ->
            val resolved =
                CentralIdentityResolver.resolve(
                    context = context,
                    legacyChatId = relay.nodeId,
                    peerName = relay.nodeName,
                    peerIp = relay.nodeAddress,
                    useKeyStore = false,
                    reinforce = false
                )
            val quality =
                IdentityQualityReporter.score(resolved)
            val key =
                relay.nodeId.ifBlank { relay.nodeAddress }

            identities.putIfAbsent(
                key,
                RoutingShadowIdentity(
                    routeOwnerLegacyId = key,
                    routeOwnerGlobalId = resolved.globalId,
                    routeOwnerPublicKey = resolved.publicKey,
                    confidence = quality.score,
                    source = resolved.resolutionSource ?: "service-path",
                    riskLevel = riskFor(quality.label, relay.nodeName, relay.nodeAddress),
                    lastSeenAt = System.currentTimeMillis(),
                    walletAddress = resolved.walletAddress,
                    peerIp = relay.nodeAddress,
                    peerName = relay.nodeName
                )
            )
        }

        return identities.values.take(limit)
    }

    fun report(
        context: Context,
        limit: Int = 40
    ): String {
        val identities =
            inspect(context, limit)

        if (identities.isEmpty()) {
            return "No routing shadow identities yet."
        }
        val summary =
            RoutingIdentityAggregator.summarize(identities)

        return buildString {
            appendLine("ROUTING SHADOW IDENTITIES")
            appendLine("======================")
            appendLine(
                "total=${summary.totalRoutesInspected} | canonicalReady=${summary.canonicalReadyCount} | mixed=${summary.mixedCount} | conflicted=${summary.conflictedCount} | legacyOnly=${summary.legacyOnlyCount}"
            )
            appendLine("avg=${summary.averageConfidence}")
            appendLine()
            identities.forEach { item ->
                val conflict =
                    IdentityConflictClassifier.fromRoutingIdentity(item)
                val primary =
                    IdentityDisplayFormatter.primaryLabel(
                        globalId = item.routeOwnerGlobalId,
                        publicKey = item.routeOwnerPublicKey,
                        walletAddress = item.walletAddress,
                        legacyName = item.peerName,
                        ipAddress = item.peerIp
                    )
                val secondary =
                    IdentityDisplayFormatter.secondaryLabel(
                        primaryLabel = primary,
                        legacyName = item.routeOwnerLegacyId,
                        walletAddress = item.walletAddress,
                        globalId = item.routeOwnerGlobalId,
                        publicKey = item.routeOwnerPublicKey,
                        ipAddress = item.peerIp
                    )
                append(primary)
                secondary?.let {
                    append(" | ")
                    append(it)
                }
                append(" | legacy=")
                append(item.routeOwnerLegacyId)
                append(" | canonical=")
                append(item.routeOwnerGlobalId ?: "unknown")
                append(" | confidence=")
                append(item.confidence)
                append(" | source=")
                append(item.source)
                append(" | risk=")
                append(item.riskLevel)
                append(" | conflict=")
                append(conflict.type)
                append(" | severity=")
                append(conflict.severity)
                append(" | action=")
                append(conflict.suggestedAction)
                appendLine()
            }
        }.trim()
    }

    private fun riskFor(
        qualityLabel: String,
        legacyName: String?,
        ipAddress: String?
    ): String {
        return when {
            qualityLabel == "strong" || qualityLabel == "good" -> "low"
            qualityLabel == "partial" && !legacyName.isNullOrBlank() -> "medium"
            qualityLabel == "weak" && !ipAddress.isNullOrBlank() -> "high"
            else -> "unknown"
        }
    }
}
