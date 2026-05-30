package com.ghalbitnet.meshx2.identity

import android.content.Context
import com.ghalbitnet.meshx2.chat.ConversationIdentityStore
import com.ghalbitnet.meshx2.economy.EconomyParticipantDiagnostics

object IdentityDedupReporter {

    fun candidates(
        context: Context,
        limit: Int = 40
    ): List<SoftDedupCandidate> {
        val conversationIdentities =
            ConversationIdentityStore.all(context)
                .take(limit)
                .map { metadata ->
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
        val economyParticipants =
            EconomyParticipantDiagnostics.inspect(context, limit)
        val items = mutableListOf<SoftDedupCandidate>()

        fun shortRef(
            legacy: String? = null,
            ip: String? = null
        ): String? {
            return listOfNotNull(
                legacy?.takeIf { it.isNotBlank() },
                ip?.takeIf { it.isNotBlank() }
            ).joinToString(" | ").ifBlank { null }
        }

        fun addCandidate(
            strength: String,
            reason: String,
            leftLabel: String,
            rightLabel: String,
            leftReference: String? = null,
            rightReference: String? = null,
            sameWalletAddress: Boolean = false,
            samePublicKey: Boolean = false,
            sameGlobalId: Boolean = false,
            sameConversationStoreMapping: Boolean = false,
            sameIdentityRegistryMapping: Boolean = false,
            sameIp: Boolean = false,
            sameDisplayName: Boolean = false,
            conflictingWalletAddress: Boolean = false,
            conflictingPublicKey: Boolean = false,
            conflictingGlobalId: Boolean = false
        ) {
            val candidate =
                SoftDedupCandidate(
                    strength = strength,
                    reason = reason,
                    leftLabel = leftLabel,
                    rightLabel = rightLabel,
                    leftReference = leftReference,
                    rightReference = rightReference,
                    sameWalletAddress = sameWalletAddress,
                    samePublicKey = samePublicKey,
                    sameGlobalId = sameGlobalId,
                    sameConversationStoreMapping = sameConversationStoreMapping,
                    sameIdentityRegistryMapping = sameIdentityRegistryMapping,
                    sameIp = sameIp,
                    sameDisplayName = sameDisplayName,
                    conflictingWalletAddress = conflictingWalletAddress,
                    conflictingPublicKey = conflictingPublicKey,
                    conflictingGlobalId = conflictingGlobalId
                )
            if (items.none {
                    it.reason == candidate.reason &&
                        it.leftLabel == candidate.leftLabel &&
                        it.rightLabel == candidate.rightLabel
                }
            ) {
                items += candidate
            }
        }

        conversationIdentities.groupBy { it.walletAddress?.takeIf { value -> value.isNotBlank() } }
            .filterKeys { it != null }
            .values
            .filter { it.size > 1 }
            .forEach { group ->
                val base = group.first()
                group.drop(1).forEach { other ->
                    addCandidate(
                        strength = "strong",
                        reason = "same walletAddress but different legacy conversation references",
                        leftLabel = base.primaryLabel,
                        rightLabel = other.primaryLabel,
                        leftReference = shortRef(base.legacyChatId, base.peerIp),
                        rightReference = shortRef(other.legacyChatId, other.peerIp),
                        sameWalletAddress = true,
                        sameConversationStoreMapping = true,
                        sameIdentityRegistryMapping = true,
                        sameIp = base.peerIp == other.peerIp,
                        sameDisplayName = base.displayName == other.displayName,
                        conflictingPublicKey = !base.publicKey.isNullOrBlank() &&
                            !other.publicKey.isNullOrBlank() &&
                            base.publicKey != other.publicKey,
                        conflictingGlobalId = !base.globalId.isNullOrBlank() &&
                            !other.globalId.isNullOrBlank() &&
                            base.globalId != other.globalId
                    )
                }
            }

        conversationIdentities.groupBy { it.publicKey?.takeIf { value -> value.isNotBlank() } }
            .filterKeys { it != null }
            .values
            .filter { it.size > 1 }
            .forEach { group ->
                val base = group.first()
                group.drop(1).forEach { other ->
                    addCandidate(
                        strength = "strong",
                        reason = "same publicKey but different peerName/IP",
                        leftLabel = base.primaryLabel,
                        rightLabel = other.primaryLabel,
                        leftReference = shortRef(base.peerName, base.peerIp),
                        rightReference = shortRef(other.peerName, other.peerIp),
                        samePublicKey = true,
                        sameConversationStoreMapping = true,
                        sameIdentityRegistryMapping = true,
                        sameIp = base.peerIp == other.peerIp,
                        sameDisplayName = base.displayName == other.displayName,
                        sameWalletAddress = !base.walletAddress.isNullOrBlank() &&
                            base.walletAddress == other.walletAddress,
                        sameGlobalId = !base.globalId.isNullOrBlank() &&
                            base.globalId == other.globalId,
                        conflictingWalletAddress = !base.walletAddress.isNullOrBlank() &&
                            !other.walletAddress.isNullOrBlank() &&
                            base.walletAddress != other.walletAddress,
                        conflictingGlobalId = !base.globalId.isNullOrBlank() &&
                            !other.globalId.isNullOrBlank() &&
                            base.globalId != other.globalId
                    )
                }
            }

        conversationIdentities.groupBy { it.globalId?.takeIf { value -> value.isNotBlank() } }
            .filterKeys { it != null }
            .values
            .filter { it.map { identity -> identity.legacyChatId }.distinct().size > 1 }
            .forEach { group ->
                val base = group.first()
                group.drop(1).forEach { other ->
                    addCandidate(
                        strength = "medium",
                        reason = "same globalId with multiple legacy chatIds",
                        leftLabel = base.primaryLabel,
                        rightLabel = other.primaryLabel,
                        leftReference = base.legacyChatId,
                        rightReference = other.legacyChatId,
                        sameGlobalId = true,
                        sameConversationStoreMapping = true,
                        sameIdentityRegistryMapping = true,
                        sameIp = base.peerIp == other.peerIp,
                        sameDisplayName = base.displayName == other.displayName,
                        sameWalletAddress = !base.walletAddress.isNullOrBlank() &&
                            base.walletAddress == other.walletAddress,
                        samePublicKey = !base.publicKey.isNullOrBlank() &&
                            base.publicKey == other.publicKey,
                        conflictingWalletAddress = !base.walletAddress.isNullOrBlank() &&
                            !other.walletAddress.isNullOrBlank() &&
                            base.walletAddress != other.walletAddress,
                        conflictingPublicKey = !base.publicKey.isNullOrBlank() &&
                            !other.publicKey.isNullOrBlank() &&
                            base.publicKey != other.publicKey
                    )
                }
            }

        conversationIdentities.groupBy { it.displayName?.takeIf { value -> value.isNotBlank() } }
            .filterKeys { it != null }
            .values
            .filter { it.size > 1 }
            .forEach { group ->
                val weakOnes =
                    group.filter {
                        IdentityQualityReporter.score(it).label in setOf("weak", "legacy-only / unknown")
                    }
                if (weakOnes.size > 1) {
                    val base = weakOnes.first()
                    weakOnes.drop(1).forEach { other ->
                        if (base.peerIp != other.peerIp) {
                            addCandidate(
                                strength = "weak",
                                reason = "same displayName with different IP and weak quality",
                                leftLabel = base.primaryLabel,
                                rightLabel = other.primaryLabel,
                                leftReference = base.peerIp,
                                rightReference = other.peerIp,
                                sameDisplayName = true,
                                conflictingWalletAddress = !base.walletAddress.isNullOrBlank() &&
                                    !other.walletAddress.isNullOrBlank() &&
                                    base.walletAddress != other.walletAddress,
                                conflictingPublicKey = !base.publicKey.isNullOrBlank() &&
                                    !other.publicKey.isNullOrBlank() &&
                                    base.publicKey != other.publicKey,
                                conflictingGlobalId = !base.globalId.isNullOrBlank() &&
                                    !other.globalId.isNullOrBlank() &&
                                    base.globalId != other.globalId
                            )
                        }
                    }
                }
            }

        conversationIdentities.groupBy { it.peerIp.takeIf { value -> value.isNotBlank() } }
            .filterKeys { it != null }
            .values
            .filter { group -> group.map { it.peerName }.distinct().size > 1 }
            .forEach { group ->
                val base = group.first()
                group.drop(1).forEach { other ->
                    addCandidate(
                        strength = "informational",
                        reason = "same IP with different names",
                        leftLabel = base.primaryLabel,
                        rightLabel = other.primaryLabel,
                        leftReference = shortRef(base.peerName, base.peerIp),
                        rightReference = shortRef(other.peerName, other.peerIp),
                        sameIp = true,
                        sameDisplayName = base.displayName == other.displayName,
                        conflictingWalletAddress = !base.walletAddress.isNullOrBlank() &&
                            !other.walletAddress.isNullOrBlank() &&
                            base.walletAddress != other.walletAddress,
                        conflictingPublicKey = !base.publicKey.isNullOrBlank() &&
                            !other.publicKey.isNullOrBlank() &&
                            base.publicKey != other.publicKey,
                        conflictingGlobalId = !base.globalId.isNullOrBlank() &&
                            !other.globalId.isNullOrBlank() &&
                            base.globalId != other.globalId
                    )
                }
            }

        economyParticipants.forEach { participant ->
            val participantLabel =
                EconomyParticipantDiagnostics.formatParticipant(participant)
                    .substringBefore(" | source=")
            val matchingConversation =
                conversationIdentities.firstOrNull { identity ->
                    (!participant.participantGlobalId.isNullOrBlank() && participant.participantGlobalId == identity.globalId) ||
                        (!participant.participantPublicKey.isNullOrBlank() && participant.participantPublicKey == identity.publicKey) ||
                        (!participant.walletAddress.isNullOrBlank() && participant.walletAddress == identity.walletAddress)
                }
            if (matchingConversation != null) {
                addCandidate(
                    strength = "medium",
                    reason = "economy participant maps to same canonical identity as communication peer",
                    leftLabel = matchingConversation.primaryLabel,
                    rightLabel = participantLabel,
                    leftReference = shortRef(matchingConversation.legacyChatId, matchingConversation.peerIp),
                    rightReference = shortRef(participant.legacyNodeId ?: participant.legacyPeerName, participant.legacyPeerIp),
                    sameWalletAddress = !participant.walletAddress.isNullOrBlank() &&
                        participant.walletAddress == matchingConversation.walletAddress,
                    samePublicKey = !participant.participantPublicKey.isNullOrBlank() &&
                        participant.participantPublicKey == matchingConversation.publicKey,
                    sameGlobalId = !participant.participantGlobalId.isNullOrBlank() &&
                        participant.participantGlobalId == matchingConversation.globalId,
                    sameIp = !participant.legacyPeerIp.isNullOrBlank() &&
                        participant.legacyPeerIp == matchingConversation.peerIp,
                    sameDisplayName = !participant.legacyPeerName.isNullOrBlank() &&
                        participant.legacyPeerName == matchingConversation.displayName
                )
            }
        }

        return items.take(limit)
    }

    fun report(
        context: Context,
        limit: Int = 40
    ): String {
        val candidates =
            candidates(context, limit)

        if (candidates.isEmpty()) {
            return "No soft dedup candidates yet."
        }

        return buildString {
            appendLine("SOFT DEDUP CANDIDATES")
            appendLine("======================")
            candidates.forEach { candidate ->
                append(candidate.strength.uppercase())
                append(" | ")
                append(candidate.reason)
                append(" | ")
                append(candidate.leftLabel)
                candidate.leftReference?.let {
                    append(" [")
                    append(it)
                    append("]")
                }
                append(" <-> ")
                append(candidate.rightLabel)
                candidate.rightReference?.let {
                    append(" [")
                    append(it)
                    append("]")
                }
                appendLine()
            }
        }.trim()
    }
}
