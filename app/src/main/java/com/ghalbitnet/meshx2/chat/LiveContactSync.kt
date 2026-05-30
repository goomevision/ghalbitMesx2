package com.ghalbitnet.meshx2.chat

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.core.node.NodeStatusManager
import com.ghalbitnet.meshx2.util.LogThrottle
import com.ghalbitnet.meshx2.identity.CentralIdentityResolver
import com.ghalbitnet.meshx2.identity.IdentityBridge
import com.ghalbitnet.meshx2.identity.IdentityRegistry
import com.ghalbitnet.meshx2.routing.IntelligentRouteMemory

object LiveContactSync {

    fun build(context: Context): List<LiveContactItem> {
        val merged = linkedMapOf<String, LiveContactItem>()

        fun keyOf(item: LiveContactItem): String {
            return item.globalId?.takeIf { it.isNotBlank() }
                ?: item.publicKeyHash?.takeIf { it.isNotBlank() }
                ?: item.publicKey?.takeIf { it.isNotBlank() }
                ?: item.chatId
        }

        fun upsert(item: LiveContactItem) {
            val key = keyOf(item)
            val existing = merged[key]
            merged[key] =
                if (existing == null) {
                    item
                } else {
                    existing.copy(
                        chatId = existing.chatId.ifBlank { item.chatId },
                        globalId = existing.globalId ?: item.globalId,
                        publicKey = existing.publicKey ?: item.publicKey,
                        publicKeyHash = existing.publicKeyHash ?: item.publicKeyHash,
                        displayName = if (item.displayName.isNotBlank()) item.displayName else existing.displayName,
                        walletAddress = existing.walletAddress ?: item.walletAddress,
                        lastSeen = maxOf(existing.lastSeen ?: 0L, item.lastSeen ?: 0L).takeIf { it > 0L },
                        routeHint = existing.routeHint ?: item.routeHint,
                        verificationStatus =
                            when {
                                existing.verificationStatus == PeerVerificationStatus.VERIFIED || item.verificationStatus == PeerVerificationStatus.VERIFIED ->
                                    PeerVerificationStatus.VERIFIED
                                existing.verificationStatus == PeerVerificationStatus.PROVISIONAL || item.verificationStatus == PeerVerificationStatus.PROVISIONAL ->
                                    PeerVerificationStatus.PROVISIONAL
                                else -> PeerVerificationStatus.STALE
                            },
                        isSaved = existing.isSaved || item.isSaved,
                        isLive = existing.isLive || item.isLive,
                        isOffline = (existing.isOffline && item.isOffline) && !(existing.isLive || item.isLive)
                    )
                }
        }

        ConversationIdentityStore.all(context).forEach { metadata ->
            upsert(
                LiveContactItem(
                    chatId = metadata.chatId,
                    globalId = metadata.globalId,
                    publicKey = metadata.publicKey,
                    publicKeyHash = metadata.publicKeyHash,
                    displayName = metadata.canonicalDisplayName ?: metadata.chatId,
                    walletAddress = metadata.walletAddress,
                    lastSeen = metadata.lastSeen ?: metadata.updatedAt,
                    routeHint = metadata.routeHint,
                    verificationStatus = metadata.verificationStatus,
                    isSaved = true,
                    isLive = false,
                    isOffline = true
                )
            )
        }

        IdentityRegistry.all().forEach { record ->
            upsert(
                LiveContactItem(
                    chatId = record.displayName ?: record.globalId,
                    globalId = record.globalId,
                    publicKey = record.publicKey,
                    publicKeyHash = record.publicKey?.let { com.ghalbitnet.meshx2.call.CallManager.publicKeyHash(it) },
                    displayName = record.displayName ?: record.globalId,
                    walletAddress = record.walletAddress,
                    lastSeen = record.lastSeen,
                    routeHint = record.lastKnownIp,
                    verificationStatus = if (!record.publicKey.isNullOrBlank() || !record.globalId.isNullOrBlank()) PeerVerificationStatus.VERIFIED else PeerVerificationStatus.PROVISIONAL,
                    isSaved = false,
                    isLive = false,
                    isOffline = true
                )
            )
        }

        NodeStatusManager.getOnlineNodes().forEach { node ->
            val bridged = IdentityBridge.fromMeshNode(node)
            val resolved =
                CentralIdentityResolver.resolve(
                    context = context,
                    legacyChatId = node.name,
                    peerName = node.name,
                    peerIp = node.ipAddress,
                    globalIdHint = bridged.globalId,
                    publicKeyHint = bridged.publicKey,
                    walletAddressHint = bridged.walletAddress,
                    displayNameHint = bridged.displayName,
                    useKeyStore = false
                )
            val routeHint =
                IntelligentRouteMemory.getHint(context, resolved.globalId ?: node.name)?.nextHopId
                    ?: IntelligentRouteMemory.getHint(context, node.name)?.nextHopId
                    ?: node.ipAddress
            upsert(
                LiveContactItem(
                    chatId = resolved.legacyChatId,
                    globalId = resolved.globalId,
                    publicKey = resolved.publicKey,
                    publicKeyHash = resolved.publicKey?.let { com.ghalbitnet.meshx2.call.CallManager.publicKeyHash(it) },
                    displayName = resolved.displayName ?: node.name,
                    walletAddress = resolved.walletAddress,
                    lastSeen = maxOf(node.lastSeen, resolved.resolvedAt),
                    routeHint = routeHint,
                    verificationStatus =
                        if (!resolved.publicKey.isNullOrBlank() || !resolved.globalId.isNullOrBlank() || !node.publicKey.isNullOrBlank()) {
                            PeerVerificationStatus.VERIFIED
                        } else {
                            PeerVerificationStatus.PROVISIONAL
                        },
                    isSaved = ConversationIdentityStore.get(context, resolved.legacyChatId) != null,
                    isLive = node.online,
                    isOffline = !node.online
                )
            )
            ConversationIdentityStore.upsert(
                context = context,
                chatId = resolved.legacyChatId,
                metadata = ConversationIdentityMetadata(
                    chatId = resolved.legacyChatId,
                    globalId = resolved.globalId,
                    publicKey = resolved.publicKey,
                    publicKeyHash = resolved.publicKey?.let { com.ghalbitnet.meshx2.call.CallManager.publicKeyHash(it) },
                    walletAddress = resolved.walletAddress,
                    canonicalDisplayName = resolved.displayName,
                    lastSeen = maxOf(node.lastSeen, resolved.resolvedAt),
                    routeHint = routeHint,
                    verificationStatus =
                        if (!resolved.publicKey.isNullOrBlank() || !resolved.globalId.isNullOrBlank() || !node.publicKey.isNullOrBlank()) {
                            PeerVerificationStatus.VERIFIED
                        } else {
                            PeerVerificationStatus.PROVISIONAL
                        },
                    updatedAt = maxOf(node.lastSeen, resolved.resolvedAt)
                )
            )
            Log.d(
                "GHALBIT-CONTACT-LIVE",
                "live chatId=${resolved.legacyChatId} globalId=${resolved.globalId ?: "-"} route=${routeHint ?: "-"} lastSeen=${node.lastSeen} verification=${if (!resolved.publicKey.isNullOrBlank() || !resolved.globalId.isNullOrBlank() || !node.publicKey.isNullOrBlank()) "VERIFIED" else "PROVISIONAL"}"
            )
        }

        val hasLive = merged.values.any { it.isLive }
        LogThrottle.d(
            "GHALBIT-CONTACT-SYNC",
            "contact-sync:${merged.size}:$hasLive:${NodeStatusManager.onlineCount()}",
            "contacts=${merged.size} hasLive=$hasLive onlineNodes=${NodeStatusManager.onlineCount()}",
            8_000L,
            context
        )

        return merged.values
            .map {
                if (it.isLive) it.copy(isOffline = false) else it
            }
            .sortedWith(
                compareByDescending<LiveContactItem> { it.isLive }
                    .thenByDescending { it.lastSeen ?: 0L }
                    .thenBy { it.displayName.lowercase() }
            )
    }
}
