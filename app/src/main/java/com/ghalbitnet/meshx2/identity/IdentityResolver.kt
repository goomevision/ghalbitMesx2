package com.ghalbitnet.meshx2.identity

import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.model.MeshNode

object IdentityResolver {

    fun resolveGlobalId(
        globalId: String? = null,
        publicKey: String? = null,
        legacyPeerId: String? = null,
        walletAddress: String? = null,
        ipAddress: String? = null
    ): String? {
        if (!globalId.isNullOrBlank()) {
            return globalId.trim()
        }

        if (!publicKey.isNullOrBlank()) {
            return GlobalMeshIdentityManager.buildGlobalId(publicKey)
        }

        // TODO unified identity migration:
        // remove legacy peerId/IP fallbacks after discovery, contacts, and chat
        // persist canonical globalId for every known peer.
        return when {
            !legacyPeerId.isNullOrBlank() -> legacyPeerId.trim()
            !walletAddress.isNullOrBlank() -> walletAddress.trim()
            !ipAddress.isNullOrBlank() -> ipAddress.trim()
            else -> null
        }
    }

    fun resolveDisplayName(
        record: GhalbitIdentityRecord? = null,
        displayName: String? = null,
        peerName: String? = null,
        nodeName: String? = null
    ): String {
        return record?.displayName?.takeIf { it.isNotBlank() }
            ?: displayName?.takeIf { it.isNotBlank() }
            ?: peerName?.takeIf { it.isNotBlank() }
            ?: nodeName?.takeIf { it.isNotBlank() }
            ?: record?.globalId
            ?: "UNKNOWN"
    }

    fun resolveWallet(
        record: GhalbitIdentityRecord? = null,
        walletAddress: String? = null
    ): String? {
        return record?.walletAddress?.takeIf { it.isNotBlank() }
            ?: walletAddress?.takeIf { it.isNotBlank() }
    }

    fun resolveLastKnownIp(
        record: GhalbitIdentityRecord? = null,
        ipAddress: String? = null,
        node: MeshNode? = null
    ): String? {
        return record?.lastKnownIp?.takeIf { it.isNotBlank() }
            ?: ipAddress?.takeIf { it.isNotBlank() }
            ?: node?.ipAddress?.takeIf { it.isNotBlank() }
    }
}
