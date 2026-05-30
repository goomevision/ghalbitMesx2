package com.ghalbitnet.meshx2.identity

import android.content.Context
import com.ghalbitnet.meshx2.chat.ConversationIdentityStore
import com.ghalbitnet.meshx2.economy.EconomyParticipantAggregator
import com.ghalbitnet.meshx2.economy.EconomyParticipantDiagnostics

object IdentityDiagnosticsHub {

    fun report(
        context: Context,
        limit: Int = 20
    ): String {
        val communicationResolved =
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
        val communicationSummary =
            IdentityQualityAggregator.summarize(communicationResolved)
        val economySummary =
            EconomyParticipantAggregator.summarize(
                EconomyParticipantDiagnostics.inspect(context, limit)
            )
        val dedupSummary =
            DedupRiskReporter.summarize(
                IdentityDedupReporter.candidates(context, limit * 2)
            )
        val gate =
            MigrationGateEvaluator.evaluate(context)
        val routingSummary =
            RoutingIdentityAggregator.summarize(
                RoutingShadowIdentityReporter.inspect(context, limit * 2)
            )

        return buildString {
            appendLine("IDENTITY DIAGNOSTICS HUB")
            appendLine("======================")
            appendLine()
            appendLine("COMMUNICATION IDENTITY HEALTH")
            appendLine(
                "total=${communicationSummary.totalIdentities} | strong=${communicationSummary.strongCount} | good=${communicationSummary.goodCount} | partial=${communicationSummary.partialCount} | weak=${communicationSummary.weakCount} | legacy=${communicationSummary.legacyOnlyOrUnknownCount} | avg=${communicationSummary.averageScore}"
            )
            appendLine()
            appendLine("ECONOMY IDENTITY HEALTH")
            appendLine(
                "total=${economySummary.totalParticipants} | canonicalReady=${economySummary.canonicalReadyCount} | wallet=${economySummary.walletBasedCount} | publicKey=${economySummary.publicKeyBasedCount} | legacy=${economySummary.nodeOrIpLegacyCount} | unknown=${economySummary.unknownCount} | avg=${economySummary.averageConfidence}"
            )
            appendLine()
            appendLine("DEDUP RISK")
            appendLine(
                "total=${dedupSummary.totalCandidates} | high=${dedupSummary.highConfidenceCount} | likely=${dedupSummary.likelyCount} | possible=${dedupSummary.possibleCount} | weak=${dedupSummary.weakCount} | unsafe=${dedupSummary.unsafeOrAmbiguousCount}"
            )
            appendLine()
            appendLine("MIGRATION READINESS")
            appendLine(
                "status=${gate.category} | identityAvg=${gate.identityAverage} | shadowAvg=${gate.shadowAverage} | economyAvg=${gate.economyAverage} | dedupUnsafe=${gate.dedupUnsafeCount}"
            )
            appendLine(
                "coverage=${gate.resolverCoverage} | rollback=${gate.rollbackReadiness} | diagnostics=${gate.diagnosticsCompleteness}"
            )
            appendLine()
            appendLine("ROUTING READINESS")
            appendLine(
                "total=${routingSummary.totalRoutesInspected} | canonicalReady=${routingSummary.canonicalReadyCount} | mixed=${routingSummary.mixedCount} | conflicted=${routingSummary.conflictedCount} | legacyOnly=${routingSummary.legacyOnlyCount} | avg=${routingSummary.averageConfidence}"
            )
        }.trim()
    }
}
