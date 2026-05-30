package com.ghalbitnet.meshx2.identity

import android.content.Context

object MigrationDryRunReporter {

    fun inspect(
        context: Context,
        limit: Int = 40
    ): List<MigrationDryRunCandidate> {
        return ShadowCanonicalMappingReporter.inspect(context, limit)
            .map { mapping ->
                val primary =
                    IdentityDisplayFormatter.primaryLabel(
                        canonicalDisplayName = null,
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

                val canonicalReference =
                    shortCanonicalReference(mapping)
                val classification =
                    classify(mapping)
                val reason =
                    reasonFor(mapping, classification)
                val rollbackRequirement =
                    rollbackFor(classification)

                MigrationDryRunCandidate(
                    legacyChatId = mapping.legacyChatId,
                    primaryLabel = primary,
                    secondaryLabel = secondary,
                    canonicalReference = canonicalReference,
                    confidence = mapping.confidence,
                    riskLevel = mapping.riskLevel,
                    classification = classification,
                    reason = reason,
                    rollbackRequirement = rollbackRequirement,
                    mappingSource = mapping.source
                )
            }
    }

    fun summarize(
        candidates: List<MigrationDryRunCandidate>
    ): MigrationDryRunSummary {
        if (candidates.isEmpty()) {
            return MigrationDryRunSummary(
                totalCandidates = 0,
                safeCount = 0,
                riskyCount = 0,
                blockedCount = 0,
                averageConfidence = 0
            )
        }

        return MigrationDryRunSummary(
            totalCandidates = candidates.size,
            safeCount = candidates.count { it.classification == "safe candidate" },
            riskyCount = candidates.count { it.classification == "risky candidate" },
            blockedCount = candidates.count { it.classification == "blocked candidate" },
            averageConfidence = candidates.map { it.confidence }.average().toInt()
        )
    }

    fun report(
        context: Context,
        limit: Int = 40
    ): String {
        val candidates =
            inspect(context, limit)

        if (candidates.isEmpty()) {
            return "No migration dry-run candidates yet."
        }

        val summary =
            summarize(candidates)

        return buildString {
            appendLine("MIGRATION DRY-RUN SKELETON")
            appendLine("======================")
            appendLine(
                "total=${summary.totalCandidates} | safe=${summary.safeCount} | risky=${summary.riskyCount} | blocked=${summary.blockedCount} | avg=${summary.averageConfidence}"
            )
            appendLine()
            candidates.forEach { candidate ->
                val conflict =
                    IdentityConflictClassifier.fromMigrationCandidate(candidate)
                append(candidate.classification)
                append(" | ")
                append(candidate.primaryLabel)
                candidate.secondaryLabel?.let {
                    append(" | ")
                    append(it)
                }
                append(" | legacy=")
                append(candidate.legacyChatId)
                append(" | canonical=")
                append(candidate.canonicalReference ?: "unknown")
                append(" | confidence=")
                append(candidate.confidence)
                append(" | risk=")
                append(candidate.riskLevel)
                append(" | source=")
                append(candidate.mappingSource)
                append(" | conflict=")
                append(conflict.type)
                append(" | severity=")
                append(conflict.severity)
                append(" | action=")
                append(conflict.suggestedAction)
                append(" | reason=")
                append(candidate.reason)
                append(" | rollback=")
                append(candidate.rollbackRequirement)
                appendLine()
            }
        }.trim()
    }

    private fun classify(
        mapping: ShadowCanonicalMapping
    ): String {
        val hasCanonicalTarget =
            !mapping.canonicalGlobalId.isNullOrBlank() ||
                !mapping.walletAddress.isNullOrBlank() ||
                !mapping.publicKey.isNullOrBlank()

        return when {
            !hasCanonicalTarget -> "blocked candidate"
            mapping.riskLevel == "high" || mapping.riskLevel == "unknown" -> "blocked candidate"
            mapping.confidence >= 80 && mapping.riskLevel == "low" -> "safe candidate"
            else -> "risky candidate"
        }
    }

    private fun reasonFor(
        mapping: ShadowCanonicalMapping,
        classification: String
    ): String {
        val hasCanonicalTarget =
            !mapping.canonicalGlobalId.isNullOrBlank() ||
                !mapping.walletAddress.isNullOrBlank() ||
                !mapping.publicKey.isNullOrBlank()

        return when {
            !hasCanonicalTarget -> "Belum ada target canonical yang cukup kuat"
            classification == "safe candidate" -> "Shadow mapping terlihat konsisten dan berisiko rendah"
            mapping.riskLevel == "high" -> "Ada sinyal konflik atau dedup berisiko tinggi"
            mapping.riskLevel == "unknown" -> "Risiko belum cukup jelas untuk simulasi aman"
            else -> "Perlu validasi tambahan sebelum simulasi migrasi"
        }
    }

    private fun rollbackFor(
        classification: String
    ): String {
        return when (classification) {
            "safe candidate" -> "Cukup abaikan hasil dry-run; owner legacy tetap utama"
            "risky candidate" -> "Pastikan fallback ke owner legacy dan buang metadata simulasi"
            else -> "Blok simulasi; jangan hasilkan owner canonical operasional"
        }
    }

    private fun shortCanonicalReference(
        mapping: ShadowCanonicalMapping
    ): String? {
        return when {
            !mapping.canonicalGlobalId.isNullOrBlank() ->
                "Node ${shortValue(mapping.canonicalGlobalId, 8, 4)}"
            !mapping.walletAddress.isNullOrBlank() ->
                "Wallet ${shortValue(mapping.walletAddress, 6, 4)}"
            !mapping.publicKey.isNullOrBlank() ->
                "Key ${shortValue(mapping.publicKey, 8, 4)}"
            else -> null
        }
    }

    private fun shortValue(
        value: String,
        prefix: Int,
        suffix: Int
    ): String {
        if (value.length <= prefix + suffix + 1) {
            return value
        }
        return "${value.take(prefix)}...${value.takeLast(suffix)}"
    }
}
