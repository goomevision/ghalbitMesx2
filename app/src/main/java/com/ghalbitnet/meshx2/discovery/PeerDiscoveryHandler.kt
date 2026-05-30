package com.ghalbitnet.meshx2.discovery

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.call.CallManager
import com.ghalbitnet.meshx2.chat.ConversationIdentityMetadata
import com.ghalbitnet.meshx2.chat.ConversationIdentityStore
import com.ghalbitnet.meshx2.chat.PeerVerificationStatus
import com.ghalbitnet.meshx2.core.runtime.PacketTraceEntry
import com.ghalbitnet.meshx2.core.runtime.PacketTraceStore
import com.ghalbitnet.meshx2.core.node.NodeStatusManager
import com.ghalbitnet.meshx2.identity.CentralIdentityResolver
import com.ghalbitnet.meshx2.identity.GhalbitIdentityRecord
import com.ghalbitnet.meshx2.identity.IdentityDiagnosticsFormatter
import com.ghalbitnet.meshx2.identity.IdentityBridge
import com.ghalbitnet.meshx2.identity.IdentityRegistry
import com.ghalbitnet.meshx2.model.MeshNode
import com.ghalbitnet.meshx2.routing.IntelligentRouteMemory
import com.ghalbitnet.meshx2.routing.RouteDiscovery
import com.ghalbitnet.meshx2.routing.RouteHint
import com.ghalbitnet.meshx2.security.KeyStoreManager

class PeerDiscoveryHandler(
    private val context: Context,
    private val keyStore: KeyStoreManager
) {

    data class Result(
        val peerId: String,
        val ipAddress: String,
        val discoveredNode: MeshNode,
        val identityRecord: GhalbitIdentityRecord,
        val peerKeyChanged: Boolean,
        val trustScore: Int
    )

    fun handleDiscoveredNode(
        peerId: String,
        ipAddress: String,
        publicKey: String,
        gateway: Boolean,
        relay: Boolean,
        sourceGlobalId: String? = null,
        sourcePublicKeyHash: String? = null
    ): Result {
        val peerKeyChanged =
            publicKey.isNotEmpty() &&
                keyStore.isPeerKeyChanged(peerId, publicKey)

        if (publicKey.isNotEmpty()) {
            keyStore.storePeerKey(peerId, publicKey)
            keyStore.storePeerAddress(peerId, ipAddress)
        }

        val trustScore =
            if (peerKeyChanged) 0 else 50

        val discoveredNode =
            MeshNode(
                name = peerId,
                ipAddress = ipAddress,
                publicKey = publicKey,
                trusted = trustScore,
                online = true,
                gateway = gateway,
                relay = relay
            )

        val bridgedIdentity =
            IdentityBridge.fromDiscoveryPacket(
                peerId = peerId,
                ipAddress = ipAddress,
                publicKey = publicKey,
                displayName = peerId,
                lastSeen = discoveredNode.lastSeen,
                trustScore = trustScore,
                relayCapable = relay,
                gatewayCapable = gateway
            )
        IdentityRegistry.upsert(bridgedIdentity)

        val resolvedIdentity =
            CentralIdentityResolver.resolve(
                context = context,
                legacyChatId = peerId,
                peerName = peerId,
                peerIp = ipAddress,
                globalIdHint = sourceGlobalId ?: bridgedIdentity.globalId,
                publicKeyHint = bridgedIdentity.publicKey,
                walletAddressHint = bridgedIdentity.walletAddress,
                displayNameHint = bridgedIdentity.displayName
            )

        val identityRecord =
            IdentityRegistry.upsert(
                resolvedIdentity.toIdentityRecord().copy(
                    trustScore = trustScore,
                    relayCapable = relay,
                    gatewayCapable = gateway
                )
            )
        val verificationStatus =
            when {
                !sourceGlobalId.isNullOrBlank() || !sourcePublicKeyHash.isNullOrBlank() || publicKey.isNotBlank() -> PeerVerificationStatus.VERIFIED
                System.currentTimeMillis() - discoveredNode.lastSeen > 120000L -> PeerVerificationStatus.STALE
                else -> PeerVerificationStatus.PROVISIONAL
            }

        // TODO unified identity:
        // discovery now emits canonical identity records, but routing and chat
        // still consume legacy peerId/IP fields until later migration phases.
        NodeStatusManager.upsertNode(discoveredNode)
        DiscoveryManager.addNode(discoveredNode)
        RouteDiscovery.rememberDirectRoute(
            destinationPeerId = peerId,
            destinationIp = ipAddress,
            trustScore = trustScore
        )
        IntelligentRouteMemory.rememberHint(
            context = context,
            hint =
                RouteHint(
                    destinationId = sourceGlobalId ?: peerId,
                    nextHopId = ipAddress,
                    latencyMs = 0L,
                    hopCount = 1,
                    trustScore = trustScore.coerceAtLeast(40),
                    lastSeen = discoveredNode.lastSeen
                )
        )
        ConversationIdentityStore.upsert(
            context = context,
            chatId = peerId,
            metadata =
                ConversationIdentityMetadata(
                    chatId = peerId,
                    globalId = sourceGlobalId ?: identityRecord.globalId,
                    publicKey = publicKey.takeIf { it.isNotBlank() } ?: identityRecord.publicKey,
                    publicKeyHash = sourcePublicKeyHash ?: CallManager.publicKeyHash(publicKey.takeIf { it.isNotBlank() } ?: identityRecord.publicKey),
                    walletAddress = identityRecord.walletAddress,
                    canonicalDisplayName = identityRecord.displayName,
                    lastSeen = discoveredNode.lastSeen,
                    routeHint = ipAddress,
                    verificationStatus = verificationStatus,
                    updatedAt = System.currentTimeMillis()
                )
        )
        PacketTraceStore.record(
            PacketTraceEntry(
                packetType = "DISCOVERY_HELLO",
                sourceNodeId = peerId,
                targetNodeId = "LOCAL",
                routeType = "LOCAL_MESH_DIRECT",
                transport = "UDP_DISCOVERY",
                deliveryState = verificationStatus.name
            )
        )
        Log.d(
            "GHALBIT-PEER-PIPELINE",
            "source=$peerId state=${verificationStatus.name} route=LOCAL_MESH globalId=${sourceGlobalId ?: identityRecord.globalId ?: "-"} ip=$ipAddress"
        )

        Log.d(
            "GHALBIT",
            "DISCOVERY_IDENTITY ${
                IdentityDiagnosticsFormatter.formatResolved(
                    resolvedIdentity.copy(
                        resolutionSource = "discovery:${resolvedIdentity.resolutionSource ?: "bridge"}",
                        resolvedAt = identityRecord.lastSeen,
                        peerIp = identityRecord.lastKnownIp ?: resolvedIdentity.peerIp
                    )
                )
            }"
        )

        return Result(
            peerId = peerId,
            ipAddress = ipAddress,
            discoveredNode = discoveredNode,
            identityRecord = identityRecord,
            peerKeyChanged = peerKeyChanged,
            trustScore = trustScore
        )
    }
}
