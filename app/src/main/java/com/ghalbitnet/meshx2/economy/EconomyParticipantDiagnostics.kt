package com.ghalbitnet.meshx2.economy

import android.content.Context
import com.ghalbitnet.meshx2.blockchain.BlockDatabase
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.identity.CentralIdentityResolver
import com.ghalbitnet.meshx2.identity.IdentityDisplayFormatter
import com.ghalbitnet.meshx2.identity.IdentityConflictClassifier
import com.ghalbitnet.meshx2.security.KeyStoreManager
import com.ghalbitnet.meshx2.token.TokenDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

object EconomyParticipantDiagnostics {

    fun inspect(
        context: Context,
        entryLimit: Int = 20
    ): List<EconomyParticipantIdentity> {
        val participantsByKey =
            linkedMapOf<String, EconomyParticipantIdentity>()

        fun participantKey(item: EconomyParticipantIdentity): String {
            return item.participantGlobalId
                ?.takeIf { it.isNotBlank() }
                ?: item.participantPublicKey
                ?.takeIf { it.isNotBlank() }
                ?: item.walletAddress
                ?.takeIf { it.isNotBlank() }
                ?: item.legacyNodeId
                ?.takeIf { it.isNotBlank() }
                ?: item.legacyPeerName
                ?.takeIf { it.isNotBlank() }
                ?: item.legacyPeerIp
                ?.takeIf { it.isNotBlank() }
                ?: "unknown-${participantsByKey.size}"
        }

        fun chooseText(
            current: String?,
            incoming: String?
        ): String? {
            return current?.takeIf { it.isNotBlank() }
                ?: incoming?.takeIf { it.isNotBlank() }
        }

        fun chooseSource(
            current: EconomyIdentitySource,
            incoming: EconomyIdentitySource
        ): EconomyIdentitySource {
            return if (incomingPriority(incoming) > incomingPriority(current)) incoming else current
        }

        fun upsert(item: EconomyParticipantIdentity) {
            val key =
                participantKey(item)
            val existing =
                participantsByKey[key]

            participantsByKey[key] =
                if (existing == null) {
                    item
                } else {
                    EconomyParticipantIdentity(
                        participantGlobalId = chooseText(existing.participantGlobalId, item.participantGlobalId),
                        participantPublicKey = chooseText(existing.participantPublicKey, item.participantPublicKey),
                        walletAddress = chooseText(existing.walletAddress, item.walletAddress),
                        legacyNodeId = chooseText(existing.legacyNodeId, item.legacyNodeId),
                        legacyPeerName = chooseText(existing.legacyPeerName, item.legacyPeerName),
                        legacyPeerIp = chooseText(existing.legacyPeerIp, item.legacyPeerIp),
                        source = chooseSource(existing.source, item.source),
                        confidence = maxOf(existing.confidence, item.confidence),
                        lastSeenAt = maxOf(existing.lastSeenAt, item.lastSeenAt)
                    )
                }
        }

        val keyStore =
            KeyStoreManager(context)
        val localGlobalId =
            GlobalMeshIdentityManager.buildGlobalId(keyStore.publicKeyBase64)

        upsert(
            EconomyParticipantIdentity(
                participantGlobalId = localGlobalId,
                participantPublicKey = keyStore.publicKeyBase64,
                legacyNodeId = localGlobalId,
                legacyPeerName = localGlobalId,
                source = EconomyIdentitySource.LOCAL_GLOBAL_ID,
                confidence = 95,
                lastSeenAt = System.currentTimeMillis()
            )
        )

        MeshServiceLedger.recentEntries(context, entryLimit).forEach { entry ->
            upsert(
                EconomyParticipantIdentity(
                    participantGlobalId = entry.session.userGlobalId.takeIf { it.isNotBlank() },
                    legacyNodeId = entry.session.userGlobalId.takeIf { it.isNotBlank() },
                    legacyPeerName = entry.session.userGlobalId.takeIf { it.isNotBlank() },
                    source = EconomyIdentitySource.LOCAL_GLOBAL_ID,
                    confidence = 90,
                    lastSeenAt = entry.session.endedAt
                )
            )

            if (entry.session.localInternetProvider) {
                upsert(
                    EconomyParticipantIdentity(
                        participantGlobalId = entry.session.userGlobalId.takeIf { it.isNotBlank() },
                        legacyNodeId = entry.session.gatewayNodeId.takeIf { it.isNotBlank() },
                        legacyPeerName = entry.session.gatewayNodeName.takeIf { it.isNotBlank() },
                        legacyPeerIp = entry.session.gatewayNodeAddress.takeIf { it.isNotBlank() },
                        source = EconomyIdentitySource.LOCAL_GLOBAL_ID,
                        confidence = 92,
                        lastSeenAt = entry.session.endedAt
                    )
                )
            } else {
                upsert(
                    fromResolver(
                        context = context,
                        legacyNodeId = entry.session.gatewayNodeId,
                        peerName = entry.session.gatewayNodeName,
                        peerIp = entry.session.gatewayNodeAddress,
                        lastSeenAt = entry.session.endedAt
                    )
                )
            }

            entry.session.relayPath.forEach { relay ->
                upsert(
                    fromResolver(
                        context = context,
                        legacyNodeId = relay.nodeId,
                        peerName = relay.nodeName,
                        peerIp = relay.nodeAddress,
                        lastSeenAt = entry.session.endedAt
                    )
                )
            }

            entry.settlement.relayRewards.forEach { reward ->
                upsert(
                    fromResolver(
                        context = context,
                        legacyNodeId = reward.nodeId,
                        peerName = reward.nodeName,
                        peerIp = reward.nodeAddress,
                        lastSeenAt = entry.session.endedAt
                    )
                )
            }

            upsert(
                EconomyParticipantIdentity(
                    legacyNodeId = "BUILDER_FOUNDATION",
                    legacyPeerName = "BUILDER_FOUNDATION",
                    source = EconomyIdentitySource.LEDGER_LEGACY,
                    confidence = 20,
                    lastSeenAt = entry.session.endedAt
                )
            )

            upsert(
                EconomyParticipantIdentity(
                    legacyNodeId = "TREASURY_POOL",
                    legacyPeerName = "TREASURY_POOL",
                    source = EconomyIdentitySource.LEDGER_LEGACY,
                    confidence = 20,
                    lastSeenAt = entry.session.endedAt
                )
            )
        }

        recentTokenParticipants(
            context = context,
            limit = entryLimit * 2
        ).forEach { participant ->
            upsert(participant)
        }

        recentBlockchainParticipants(
            context = context,
            limit = entryLimit
        ).forEach { participant ->
            upsert(participant)
        }

        return participantsByKey.values
            .sortedWith(
                compareByDescending<EconomyParticipantIdentity> { it.lastSeenAt }
                    .thenByDescending { it.confidence }
            )
    }

    fun report(
        context: Context,
        entryLimit: Int = 20
    ): String {
        val participants =
            inspect(context, entryLimit)

        if (participants.isEmpty()) {
            return "No economy participant identity hints yet."
        }
        val summary =
            EconomyParticipantAggregator.summarize(participants)

        return buildString {
            appendLine("ECONOMY SHADOW PARTICIPANTS")
            appendLine("======================")
            appendLine("total=${summary.totalParticipants} | canonical=${summary.canonicalReadyCount} | wallet=${summary.walletBasedCount} | publicKey=${summary.publicKeyBasedCount} | legacy=${summary.nodeOrIpLegacyCount} | unknown=${summary.unknownCount}")
            appendLine("avg=${summary.averageConfidence} | low=${summary.lowestConfidence} | high=${summary.highestConfidence}")
            appendLine()
            participants.forEach { participant ->
                appendLine(formatParticipant(participant))
            }
        }.trim()
    }

    fun formatParticipant(
        participant: EconomyParticipantIdentity
    ): String {
        val conflict =
            IdentityConflictClassifier.fromEconomyParticipant(participant)
        val primaryLabel =
            IdentityDisplayFormatter.primaryLabel(
                walletAddress = participant.walletAddress,
                globalId = participant.participantGlobalId,
                publicKey = participant.participantPublicKey,
                legacyName = participant.legacyPeerName ?: participant.legacyNodeId,
                ipAddress = participant.legacyPeerIp
            )
        val secondaryLabel =
            IdentityDisplayFormatter.secondaryLabel(
                primaryLabel = primaryLabel,
                legacyName = participant.legacyPeerName ?: participant.legacyNodeId,
                walletAddress = participant.walletAddress,
                globalId = participant.participantGlobalId,
                publicKey = participant.participantPublicKey,
                ipAddress = participant.legacyPeerIp
            )

        return buildString {
            append(primaryLabel)
            secondaryLabel?.takeIf { it.isNotBlank() }?.let {
                append(" | ")
                append(it)
            }
            append(" | source=")
            append(participant.source.name)
            append(" | confidence=")
            append(participant.confidence)
            append(" | conflict=")
            append(conflict.type)
            append(" | severity=")
            append(conflict.severity)
            append(" | action=")
            append(conflict.suggestedAction)
            val legacyReference =
                listOfNotNull(
                    participant.legacyNodeId?.takeIf { it.isNotBlank() }?.let { "node=$it" },
                    participant.legacyPeerIp?.takeIf { it.isNotBlank() }?.let { "ip=$it" }
                ).joinToString(" | ")
            if (legacyReference.isNotBlank()) {
                append(" | ")
                append(legacyReference)
            }
            append(" | seen=")
            append(participant.lastSeenAt)
        }
    }

    private fun fromResolver(
        context: Context,
        legacyNodeId: String?,
        peerName: String?,
        peerIp: String?,
        walletAddress: String? = null,
        publicKey: String? = null,
        globalId: String? = null,
        lastSeenAt: Long
    ): EconomyParticipantIdentity {
        val fallbackLegacyId =
            legacyNodeId?.takeIf { it.isNotBlank() }
                ?: peerName?.takeIf { it.isNotBlank() }
                ?: peerIp?.takeIf { it.isNotBlank() }
                ?: "UNKNOWN_REMOTE"
        val resolved =
            CentralIdentityResolver.resolve(
                context = context,
                legacyChatId = fallbackLegacyId,
                peerName = peerName ?: fallbackLegacyId,
                peerIp = peerIp,
                globalIdHint = globalId,
                publicKeyHint = publicKey,
                walletAddressHint = walletAddress,
                displayNameHint = peerName,
                useKeyStore = false
            )

        val source =
            when {
                !resolved.publicKey.isNullOrBlank() || !resolved.globalId.isNullOrBlank() ->
                    EconomyIdentitySource.PUBLIC_KEY_BRIDGE
                !resolved.walletAddress.isNullOrBlank() ->
                    EconomyIdentitySource.WALLET_ONLY
                !legacyNodeId.isNullOrBlank() ->
                    EconomyIdentitySource.NODE_ID
                !peerName.isNullOrBlank() ->
                    EconomyIdentitySource.PEER_NAME
                !peerIp.isNullOrBlank() ->
                    EconomyIdentitySource.PEER_IP
                else ->
                    EconomyIdentitySource.UNKNOWN
            }

        val confidence =
            when (source) {
                EconomyIdentitySource.PUBLIC_KEY_BRIDGE -> if (!resolved.publicKey.isNullOrBlank()) 85 else 72
                EconomyIdentitySource.WALLET_ONLY -> 60
                EconomyIdentitySource.NODE_ID -> 40
                EconomyIdentitySource.PEER_NAME -> 30
                EconomyIdentitySource.PEER_IP -> 20
                EconomyIdentitySource.LEDGER_LEGACY -> 15
                EconomyIdentitySource.LOCAL_GLOBAL_ID -> 95
                EconomyIdentitySource.UNKNOWN -> 5
            }

        return EconomyParticipantIdentity(
            participantGlobalId = resolved.globalId,
            participantPublicKey = resolved.publicKey,
            walletAddress = resolved.walletAddress,
            legacyNodeId = legacyNodeId?.takeIf { it.isNotBlank() },
            legacyPeerName = peerName?.takeIf { it.isNotBlank() },
            legacyPeerIp = resolved.peerIp.ifBlank { peerIp.orEmpty() }.ifBlank { null },
            source = source,
            confidence = confidence,
            lastSeenAt = maxOf(lastSeenAt, resolved.resolvedAt)
        )
    }

    private fun incomingPriority(
        source: EconomyIdentitySource
    ): Int {
        return when (source) {
            EconomyIdentitySource.LOCAL_GLOBAL_ID -> 7
            EconomyIdentitySource.PUBLIC_KEY_BRIDGE -> 6
            EconomyIdentitySource.WALLET_ONLY -> 5
            EconomyIdentitySource.NODE_ID -> 4
            EconomyIdentitySource.PEER_NAME -> 3
            EconomyIdentitySource.PEER_IP -> 2
            EconomyIdentitySource.LEDGER_LEGACY -> 1
            EconomyIdentitySource.UNKNOWN -> 0
        }
    }

    private fun recentTokenParticipants(
        context: Context,
        limit: Int
    ): List<EconomyParticipantIdentity> {
        val rows =
            runCatching {
                runBlocking(Dispatchers.IO) {
                    TokenDatabase.getInstance(context)
                        .tokenDao()
                        .getRecentTransactions(limit)
                }
            }.getOrDefault(emptyList())

        return rows.map { row ->
            val walletGlobalId =
                row.peerIp
                    .takeIf { it.startsWith("wallet:") }
                    ?.removePrefix("wallet:")
                    ?.takeIf { it.isNotBlank() }
            val walletAddress =
                row.peerIp.takeIf { it.startsWith("wallet:") }
            when {
                !walletGlobalId.isNullOrBlank() -> {
                    EconomyParticipantIdentity(
                        participantGlobalId = walletGlobalId,
                        walletAddress = walletAddress,
                        legacyNodeId = row.peerName.takeIf { it.isNotBlank() },
                        legacyPeerName = row.peerName.takeIf { it.isNotBlank() },
                        legacyPeerIp = row.peerIp,
                        source = EconomyIdentitySource.LOCAL_GLOBAL_ID,
                        confidence = 88,
                        lastSeenAt = row.timestamp
                    )
                }
                row.peerIp == "TREASURY_POOL" -> {
                    EconomyParticipantIdentity(
                        legacyNodeId = "TREASURY_POOL",
                        legacyPeerName = "TREASURY_POOL",
                        legacyPeerIp = row.peerIp,
                        source = EconomyIdentitySource.LEDGER_LEGACY,
                        confidence = 20,
                        lastSeenAt = row.timestamp
                    )
                }
                row.peerIp == "wallet:BUILDER_FOUNDATION" || row.peerName == "BUILDER_FOUNDATION" -> {
                    EconomyParticipantIdentity(
                        walletAddress = row.peerIp.takeIf { it.startsWith("wallet:") },
                        legacyNodeId = "BUILDER_FOUNDATION",
                        legacyPeerName = "BUILDER_FOUNDATION",
                        legacyPeerIp = row.peerIp,
                        source = EconomyIdentitySource.WALLET_ONLY,
                        confidence = 55,
                        lastSeenAt = row.timestamp
                    )
                }
                else -> {
                    fromResolver(
                        context = context,
                        legacyNodeId = row.peerName,
                        peerName = row.peerName,
                        peerIp = row.peerIp,
                        lastSeenAt = row.timestamp
                    )
                }
            }
        }
    }

    private fun recentBlockchainParticipants(
        context: Context,
        limit: Int
    ): List<EconomyParticipantIdentity> {
        val blocks =
            runCatching {
                runBlocking(Dispatchers.IO) {
                    BlockDatabase.getInstance(context)
                        .blockDao()
                        .getAll()
                        .takeLast(limit)
                }
            }.getOrDefault(emptyList())

        return blocks.mapNotNull { block ->
            val minerAddress =
                block.minerAddress.takeIf { it.isNotBlank() && it != "GENESIS" }
                    ?: return@mapNotNull null
            EconomyParticipantIdentity(
                walletAddress = minerAddress,
                legacyNodeId = minerAddress,
                legacyPeerName = minerAddress,
                source = EconomyIdentitySource.WALLET_ONLY,
                confidence = 58,
                lastSeenAt = block.timestamp
            )
        }
    }
}
