package com.ghalbitnet.meshx2.identity

import android.content.Context
import com.ghalbitnet.meshx2.chat.ConversationIdentityStore

object ShadowCanonicalMappingReporter {

    fun inspect(
        context: Context,
        limit: Int = 40
    ): List<ShadowCanonicalMapping> {
        val dedupCandidates =
            IdentityDedupReporter.candidates(context, limit)
        val riskByLegacy =
            mutableMapOf<String, String>()

        dedupCandidates.forEach { candidate ->
            val text = buildString {
                append(candidate.leftReference.orEmpty())
                append(" ")
                append(candidate.rightReference.orEmpty())
            }
            val risk =
                when (candidate.strength) {
                    "strong" -> "high"
                    "medium" -> "medium"
                    "weak" -> "low"
                    else -> "informational"
                }

            ConversationIdentityStore.all(context).forEach { metadata ->
                if (text.contains(metadata.chatId)) {
                    val current = riskByLegacy[metadata.chatId]
                    riskByLegacy[metadata.chatId] =
                        higherRisk(current, risk)
                }
            }
        }

        return ConversationIdentityStore.all(context)
            .take(limit)
            .map { metadata ->
                val resolved =
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
                val quality =
                    IdentityQualityReporter.score(resolved)

                ShadowCanonicalMapping(
                    legacyChatId = metadata.chatId,
                    canonicalGlobalId = resolved.globalId,
                    publicKey = resolved.publicKey,
                    walletAddress = resolved.walletAddress,
                    confidence = quality.score,
                    source = resolved.resolutionSource ?: "unknown",
                    riskLevel = riskByLegacy[metadata.chatId] ?: defaultRisk(quality.label),
                    lastSeenAt = resolved.resolvedAt
                )
            }
    }

    fun report(
        context: Context,
        limit: Int = 40
    ): String {
        val mappings =
            inspect(context, limit)

        if (mappings.isEmpty()) {
            return "No shadow canonical mappings yet."
        }
        val summary =
            ShadowMappingAggregator.summarize(mappings)

        return buildString {
            appendLine("SHADOW CANONICAL MAPPINGS")
            appendLine("======================")
            appendLine("total=${summary.totalMappings} | high=${summary.highConfidenceCount} | medium=${summary.mediumConfidenceCount} | low=${summary.lowConfidenceCount} | conflicted=${summary.conflictedCount} | unknown=${summary.unknownCount}")
            appendLine("avg=${summary.averageConfidence}")
            appendLine()
            mappings.forEach { mapping ->
                val primary =
                    IdentityDisplayFormatter.primaryLabel(
                        globalId = mapping.canonicalGlobalId,
                        publicKey = mapping.publicKey,
                        walletAddress = mapping.walletAddress,
                        legacyName = mapping.legacyChatId
                    )
                val secondary =
                    IdentityDisplayFormatter.secondaryLabel(
                        primaryLabel = primary,
                        legacyName = mapping.legacyChatId,
                        walletAddress = mapping.walletAddress,
                        globalId = mapping.canonicalGlobalId,
                        publicKey = mapping.publicKey
                    )
                append(primary)
                secondary?.let {
                    append(" | ")
                    append(it)
                }
                append(" | legacy=")
                append(mapping.legacyChatId)
                append(" | canonical=")
                append(mapping.canonicalGlobalId ?: "unknown")
                append(" | confidence=")
                append(mapping.confidence)
                append(" | risk=")
                append(mapping.riskLevel)
                append(" | source=")
                append(mapping.source)
                appendLine()
            }
        }.trim()
    }

    private fun defaultRisk(
        qualityLabel: String
    ): String {
        return when (qualityLabel) {
            "strong", "good" -> "low"
            "partial" -> "medium"
            "weak" -> "high"
            else -> "unknown"
        }
    }

    private fun higherRisk(
        current: String?,
        incoming: String
    ): String {
        fun weight(value: String?): Int =
            when (value) {
                "high" -> 4
                "medium" -> 3
                "low" -> 2
                "informational" -> 1
                else -> 0
            }
        return if (weight(incoming) > weight(current)) incoming else current ?: incoming
    }
}
