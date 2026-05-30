package com.ghalbitnet.meshx2.identity

import android.content.Context
import com.ghalbitnet.meshx2.chat.ConversationIdentityStore
import com.ghalbitnet.meshx2.economy.EconomyParticipantAggregator
import com.ghalbitnet.meshx2.economy.EconomyParticipantDiagnostics
import java.io.File

object MigrationGateEvaluator {

    fun evaluate(context: Context): DryRunGateStatus {
        val resolvedIdentities =
            ConversationIdentityStore.all(context).map { metadata ->
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
        val identitySummary =
            IdentityQualityAggregator.summarize(resolvedIdentities)
        val dedupSummary =
            DedupRiskReporter.summarize(
                IdentityDedupReporter.candidates(context)
            )
        val shadowSummary =
            ShadowMappingAggregator.summarize(
                ShadowCanonicalMappingReporter.inspect(context)
            )
        val economySummary =
            EconomyParticipantAggregator.summarize(
                EconomyParticipantDiagnostics.inspect(context)
            )

        val resolverCoverage =
            if (hasCoverageReport()) "documented" else "missing"
        val rollbackReadiness =
            if (hasRollbackDocs()) "documented" else "missing"
        val diagnosticsCompleteness =
            if (hasDiagnosticsInputs(context)) "complete" else "partial"

        val notes = mutableListOf<String>()
        if (identitySummary.averageScore < 40) {
            notes += "Rata-rata quality komunikasi masih rendah."
        }
        if (dedupSummary.unsafeOrAmbiguousCount > 0) {
            notes += "Masih ada kandidat dedup yang ambigu."
        }
        if (shadowSummary.averageConfidence < 50) {
            notes += "Confidence shadow mapping masih lemah."
        }
        if (economySummary.totalParticipants == 0 || economySummary.averageConfidence < 40) {
            notes += "Diagnostics identity economy belum cukup kuat."
        }
        if (rollbackReadiness != "documented") {
            notes += "Dokumen rollback/prerequisite belum lengkap."
        }
        if (diagnosticsCompleteness != "complete") {
            notes += "Input diagnostics untuk gate belum lengkap."
        }
        if (resolverCoverage != "documented") {
            notes += "Coverage resolver belum terdokumentasi."
        }
        if (notes.isEmpty()) {
            notes += "Sinyal utama tersedia untuk evaluasi dry-run."
        }

        val category =
            when {
                resolvedIdentities.isEmpty() && economySummary.totalParticipants == 0 -> "unknown"
                rollbackReadiness != "documented" || diagnosticsCompleteness == "partial" -> "blocked"
                identitySummary.averageScore >= 65 &&
                    dedupSummary.unsafeOrAmbiguousCount == 0 &&
                    shadowSummary.averageConfidence >= 65 &&
                    economySummary.averageConfidence >= 50 -> "ready"
                identitySummary.averageScore >= 45 &&
                    shadowSummary.averageConfidence >= 45 -> "guarded"
                else -> "blocked"
            }

        return DryRunGateStatus(
            category = category,
            identityAverage = identitySummary.averageScore,
            dedupUnsafeCount = dedupSummary.unsafeOrAmbiguousCount,
            shadowAverage = shadowSummary.averageConfidence,
            economyAverage = economySummary.averageConfidence,
            resolverCoverage = resolverCoverage,
            rollbackReadiness = rollbackReadiness,
            diagnosticsCompleteness = diagnosticsCompleteness,
            notes = notes
        )
    }

    fun report(context: Context): String {
        val status = evaluate(context)
        return buildString {
            appendLine("MIGRATION DRY-RUN READINESS GATE")
            appendLine("======================")
            appendLine("status=${status.category}")
            appendLine(
                "identityAvg=${status.identityAverage} | dedupUnsafe=${status.dedupUnsafeCount} | shadowAvg=${status.shadowAverage} | economyAvg=${status.economyAverage}"
            )
            appendLine(
                "coverage=${status.resolverCoverage} | rollback=${status.rollbackReadiness} | diagnostics=${status.diagnosticsCompleteness}"
            )
            appendLine()
            status.notes.forEach { note ->
                appendLine("- $note")
            }
        }.trim()
    }

    private fun hasCoverageReport(): Boolean =
        repoFile("IDENTITY_RESOLVER_COVERAGE.md").exists()

    private fun hasRollbackDocs(): Boolean =
        repoFile("CANONICAL_MIGRATION_RISK_REGISTER.md").exists() &&
            repoFile("CANONICAL_MIGRATION_STAGE_PLAN.md").exists() &&
            repoFile("CANONICAL_MIGRATION_PREREQUISITES.md").exists() &&
            repoFile("MIGRATION_DRY_RUN_SIMULATION_DESIGN.md").exists()

    private fun hasDiagnosticsInputs(context: Context): Boolean =
        ConversationIdentityStore.all(context).isNotEmpty() ||
            EconomyParticipantDiagnostics.inspect(context).isNotEmpty()

    private fun repoFile(name: String): File =
        File(contextRoot(), name)

    private fun contextRoot(): File =
        File(System.getProperty("user.dir"))
}
