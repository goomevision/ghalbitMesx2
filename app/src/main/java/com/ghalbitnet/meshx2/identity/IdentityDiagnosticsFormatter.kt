package com.ghalbitnet.meshx2.identity

import android.content.Context
import com.ghalbitnet.meshx2.chat.ConversationIdentityStore

object IdentityDiagnosticsFormatter {

    fun formatResolved(identity: ResolvedPeerIdentity): String {
        val quality =
            IdentityQualityReporter.score(identity)
        return buildString {
            append(identity.primaryLabel)
            identity.secondaryLabel?.takeIf { it.isNotBlank() }?.let {
                append(" | ")
                append(it)
            }
            append(" | owner=")
            append(identity.legacyChatId)
            identity.resolutionSource?.takeIf { it.isNotBlank() }?.let {
                append(" | source=")
                append(it)
            }
            append(" | quality=")
            append(quality.score)
            append(" (")
            append(quality.label)
            append(")")
        }
    }

    fun report(
        context: Context,
        maxEntries: Int = 20
    ): String {
        val stored =
            ConversationIdentityStore.all(context)
                .take(maxEntries)

        if (stored.isEmpty()) {
            return "No canonical ownership hints yet."
        }

        val resolvedIdentities =
            stored.map { metadata ->
                CentralIdentityResolver.resolve(
                    context = context,
                    legacyChatId = metadata.chatId,
                    peerName = metadata.chatId,
                    globalIdHint = metadata.globalId,
                    publicKeyHint = metadata.publicKey,
                    walletAddressHint = metadata.walletAddress,
                    displayNameHint = metadata.canonicalDisplayName,
                    useKeyStore = false,
                    reinforce = false
                )
            }
        val summary =
            IdentityQualityAggregator.summarize(resolvedIdentities)

        return buildString {
            appendLine("CANONICAL OWNERSHIP")
            appendLine("======================")
            appendLine("total=${summary.totalIdentities} | strong=${summary.strongCount} | good=${summary.goodCount} | partial=${summary.partialCount} | weak=${summary.weakCount} | legacy=${summary.legacyOnlyOrUnknownCount}")
            appendLine("avg=${summary.averageScore} | low=${summary.lowestScore} | high=${summary.highestScore}")
            appendLine()
            resolvedIdentities.forEach { resolved ->
                appendLine(formatResolved(resolved))
            }
        }.trim()
    }
}
