package com.ghalbitnet.meshx2.identity

import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.model.MeshNode

object IdentityBridge {

    data class DiscoveryPacketIdentitySeed(
        val peerId: String?,
        val ipAddress: String?,
        val publicKey: String?,
        val walletAddress: String? = null,
        val displayName: String? = null,
        val lastSeen: Long = System.currentTimeMillis(),
        val trustScore: Int = 0,
        val relayCapable: Boolean = false,
        val gatewayCapable: Boolean = false
    )

    data class ChatPeerIdentitySeed(
        val peerName: String?,
        val peerIp: String?,
        val publicKey: String? = null,
        val walletAddress: String? = null,
        val globalId: String? = null,
        val displayName: String? = null,
        val lastSeen: Long = System.currentTimeMillis()
    )

    fun fromMeshNode(node: MeshNode): GhalbitIdentityRecord {
        return GhalbitIdentityRecord(
            globalId = resolveStableId(
                publicKey = node.publicKey,
                walletAddress = null,
                globalId = null,
                peerId = node.name,
                ipAddress = node.ipAddress
            ),
            publicKey = node.publicKey.takeIf { it.isNotBlank() },
            walletAddress = null,
            displayName = node.name.takeIf { it.isNotBlank() },
            lastKnownIp = node.ipAddress.takeIf { it.isNotBlank() },
            lastSeen = node.lastSeen,
            trustScore = node.trusted,
            relayCapable = node.relay,
            gatewayCapable = node.gateway
        )
    }

    fun fromDiscoveryPacket(
        peerId: String?,
        ipAddress: String?,
        publicKey: String?,
        walletAddress: String? = null,
        displayName: String? = null,
        lastSeen: Long = System.currentTimeMillis(),
        trustScore: Int = 0,
        relayCapable: Boolean = false,
        gatewayCapable: Boolean = false
    ): GhalbitIdentityRecord {
        return fromDiscoveryPacket(
            DiscoveryPacketIdentitySeed(
                peerId = peerId,
                ipAddress = ipAddress,
                publicKey = publicKey,
                walletAddress = walletAddress,
                displayName = displayName,
                lastSeen = lastSeen,
                trustScore = trustScore,
                relayCapable = relayCapable,
                gatewayCapable = gatewayCapable
            )
        )
    }

    fun fromDiscoveryPacket(seed: DiscoveryPacketIdentitySeed): GhalbitIdentityRecord {
        return GhalbitIdentityRecord(
            globalId = resolveStableId(
                publicKey = seed.publicKey,
                walletAddress = seed.walletAddress,
                globalId = null,
                peerId = seed.peerId,
                ipAddress = seed.ipAddress
            ),
            publicKey = seed.publicKey.takeIf { !it.isNullOrBlank() },
            walletAddress = seed.walletAddress.takeIf { !it.isNullOrBlank() },
            displayName = seed.displayName?.takeIf { it.isNotBlank() } ?: seed.peerId?.takeIf { it.isNotBlank() },
            lastKnownIp = seed.ipAddress?.takeIf { it.isNotBlank() },
            lastSeen = seed.lastSeen,
            trustScore = seed.trustScore,
            relayCapable = seed.relayCapable,
            gatewayCapable = seed.gatewayCapable
        )
    }

    fun fromChatPeer(
        peerName: String?,
        peerIp: String?,
        publicKey: String? = null,
        walletAddress: String? = null,
        globalId: String? = null,
        displayName: String? = null,
        lastSeen: Long = System.currentTimeMillis()
    ): GhalbitIdentityRecord {
        return fromChatPeer(
            ChatPeerIdentitySeed(
                peerName = peerName,
                peerIp = peerIp,
                publicKey = publicKey,
                walletAddress = walletAddress,
                globalId = globalId,
                displayName = displayName,
                lastSeen = lastSeen
            )
        )
    }

    fun fromChatPeer(seed: ChatPeerIdentitySeed): GhalbitIdentityRecord {
        return GhalbitIdentityRecord(
            globalId = resolveStableId(
                publicKey = seed.publicKey,
                walletAddress = seed.walletAddress,
                globalId = seed.globalId,
                peerId = seed.peerName,
                ipAddress = seed.peerIp
            ),
            publicKey = seed.publicKey.takeIf { !it.isNullOrBlank() },
            walletAddress = seed.walletAddress.takeIf { !it.isNullOrBlank() },
            displayName = seed.displayName?.takeIf { it.isNotBlank() } ?: seed.peerName?.takeIf { it.isNotBlank() },
            lastKnownIp = seed.peerIp?.takeIf { it.isNotBlank() },
            lastSeen = seed.lastSeen
        )
    }

    fun merge(
        current: GhalbitIdentityRecord?,
        incoming: GhalbitIdentityRecord
    ): GhalbitIdentityRecord {
        if (current == null) {
            return incoming
        }

        return GhalbitIdentityRecord(
            globalId = resolveStableId(
                publicKey = choosePublicKey(current.publicKey, incoming.publicKey),
                walletAddress = chooseText(current.walletAddress, incoming.walletAddress),
                globalId = chooseText(current.globalId, incoming.globalId),
                peerId = chooseText(current.displayName, incoming.displayName),
                ipAddress = chooseText(current.lastKnownIp, incoming.lastKnownIp)
            ),
            publicKey = choosePublicKey(current.publicKey, incoming.publicKey),
            walletAddress = chooseText(current.walletAddress, incoming.walletAddress),
            displayName = chooseDisplayName(current.displayName, incoming.displayName),
            lastKnownIp = chooseText(incoming.lastKnownIp, current.lastKnownIp),
            lastSeen = maxOf(current.lastSeen, incoming.lastSeen),
            trustScore = maxOf(current.trustScore, incoming.trustScore),
            relayCapable = current.relayCapable || incoming.relayCapable,
            gatewayCapable = current.gatewayCapable || incoming.gatewayCapable
        )
    }

    fun resolveStableId(
        publicKey: String?,
        walletAddress: String?,
        globalId: String?,
        peerId: String?,
        ipAddress: String?
    ): String {
        if (!publicKey.isNullOrBlank()) {
            return GlobalMeshIdentityManager.buildGlobalId(publicKey)
        }

        if (!walletAddress.isNullOrBlank()) {
            return walletAddress.trim()
        }

        if (!globalId.isNullOrBlank()) {
            return globalId.trim()
        }

        if (!peerId.isNullOrBlank()) {
            return peerId.trim()
        }

        return ipAddress?.trim().orEmpty().ifBlank { "UNKNOWN" }
    }

    private fun choosePublicKey(
        current: String?,
        incoming: String?
    ): String? {
        return current?.takeIf { it.isNotBlank() }
            ?: incoming?.takeIf { it.isNotBlank() }
    }

    private fun chooseText(
        primary: String?,
        secondary: String?
    ): String? {
        return primary?.takeIf { it.isNotBlank() }
            ?: secondary?.takeIf { it.isNotBlank() }
    }

    private fun chooseDisplayName(
        current: String?,
        incoming: String?
    ): String? {
        return current?.takeIf { it.isNotBlank() }
            ?: incoming?.takeIf { it.isNotBlank() }
    }
}
