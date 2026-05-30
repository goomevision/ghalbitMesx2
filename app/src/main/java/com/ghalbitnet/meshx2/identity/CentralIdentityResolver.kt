package com.ghalbitnet.meshx2.identity

import android.content.Context
import com.ghalbitnet.meshx2.chat.ConversationIdentityStore
import com.ghalbitnet.meshx2.security.KeyStoreManager

object CentralIdentityResolver {

    fun resolve(
        context: Context,
        legacyChatId: String,
        peerName: String? = legacyChatId,
        peerIp: String? = null,
        globalIdHint: String? = null,
        publicKeyHint: String? = null,
        walletAddressHint: String? = null,
        displayNameHint: String? = null,
        useKeyStore: Boolean = true,
        reinforce: Boolean = true
    ): ResolvedPeerIdentity {
        val safeLegacyChatId =
            legacyChatId.trim().ifBlank {
                peerName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: peerIp?.trim()?.takeIf { it.isNotEmpty() }
                    ?: "UNKNOWN"
            }
        val safePeerName =
            peerName?.trim()?.takeIf { it.isNotEmpty() } ?: safeLegacyChatId
        val persisted =
            ConversationIdentityStore.get(
                context = context,
                chatId = safeLegacyChatId
            )
        val keyStore =
            if (useKeyStore) KeyStoreManager(context) else null

        val resolvedIp =
            peerIp?.trim()?.takeIf { it.isNotEmpty() }
                ?: keyStore?.getPeerAddress(safePeerName).orEmpty()
        val resolvedPublicKey =
            publicKeyHint?.trim()?.takeIf { it.isNotEmpty() }
                ?: persisted?.publicKey
                ?: keyStore?.getPeerKey(safePeerName)
        val resolvedWallet =
            walletAddressHint?.trim()?.takeIf { it.isNotEmpty() }
                ?: persisted?.walletAddress
        val resolvedGlobalHint =
            globalIdHint?.trim()?.takeIf { it.isNotEmpty() }
                ?: persisted?.globalId
        val resolvedDisplayHint =
            displayNameHint?.trim()?.takeIf { it.isNotEmpty() }
                ?: persisted?.canonicalDisplayName
        val resolutionSource =
            when {
                !globalIdHint.isNullOrBlank() -> "hint:globalId"
                !publicKeyHint.isNullOrBlank() -> "hint:publicKey"
                !walletAddressHint.isNullOrBlank() -> "hint:wallet"
                persisted != null -> "store"
                IdentityRegistry.findByLegacy(
                    peerName = safePeerName,
                    ipAddress = resolvedIp.ifBlank { null },
                    publicKey = resolvedPublicKey
                ) != null -> "registry"
                !resolvedIp.isBlank() || !safePeerName.isBlank() -> "bridge"
                else -> "legacy"
            }

        val resolvedIdentity =
            IdentityRegistry.resolveForChatTarget(
                globalId = resolvedGlobalHint,
                peerName = safePeerName,
                ipAddress = resolvedIp,
                publicKey = resolvedPublicKey,
                walletAddress = resolvedWallet
            )
                ?: IdentityRegistry.upsert(
                    IdentityBridge.fromChatPeer(
                        peerName = safePeerName,
                        peerIp = resolvedIp.ifBlank { null },
                        publicKey = resolvedPublicKey,
                        walletAddress = resolvedWallet,
                        globalId = resolvedGlobalHint,
                        displayName = resolvedDisplayHint ?: safePeerName
                    )
                )

        val resolvedAt =
            maxOf(
                resolvedIdentity.lastSeen,
                persisted?.updatedAt ?: 0L,
                System.currentTimeMillis()
            )
        val displayName =
            resolvedIdentity.displayName
                ?.takeIf { it.isNotBlank() }
                ?: resolvedDisplayHint
        val primaryLabel =
            IdentityDisplayFormatter.primaryLabel(
                canonicalDisplayName = displayName,
                walletAddress = resolvedIdentity.walletAddress ?: resolvedWallet,
                globalId = resolvedIdentity.globalId ?: resolvedGlobalHint,
                publicKey = resolvedIdentity.publicKey ?: resolvedPublicKey,
                legacyName = safePeerName,
                ipAddress = resolvedIp
            )
        val secondaryLabel =
            IdentityDisplayFormatter.secondaryLabel(
                primaryLabel = primaryLabel,
                legacyName = safeLegacyChatId,
                walletAddress = resolvedIdentity.walletAddress ?: resolvedWallet,
                globalId = resolvedIdentity.globalId ?: resolvedGlobalHint,
                publicKey = resolvedIdentity.publicKey ?: resolvedPublicKey,
                ipAddress = resolvedIp
            )

        val result =
            ResolvedPeerIdentity(
                legacyChatId = safeLegacyChatId,
                peerName = safePeerName,
                peerIp = resolvedIp,
                globalId = resolvedIdentity.globalId ?: resolvedGlobalHint,
                publicKey = resolvedIdentity.publicKey ?: resolvedPublicKey,
                walletAddress = resolvedIdentity.walletAddress ?: resolvedWallet,
                displayName = displayName,
                primaryLabel = primaryLabel,
                secondaryLabel = secondaryLabel,
                resolutionSource = resolutionSource,
                resolvedAt = resolvedAt
            )

        if (reinforce) {
            IdentityRegistry.upsert(result.toIdentityRecord())
            ConversationIdentityStore.upsert(
                context = context,
                chatId = result.legacyChatId,
                metadata = result.toConversationMetadata()
            )
        }

        return result
    }
}
