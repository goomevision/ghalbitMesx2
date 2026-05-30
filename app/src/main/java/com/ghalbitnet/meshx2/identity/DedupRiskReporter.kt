package com.ghalbitnet.meshx2.identity

import android.content.Context

object DedupRiskReporter {

    fun score(
        candidate: SoftDedupCandidate
    ): DedupRiskScore {
        var total = 0

        if (candidate.samePublicKey) total += 40
        if (candidate.sameGlobalId) total += 35
        if (candidate.sameWalletAddress) total += 30
        if (candidate.sameConversationStoreMapping) total += 15
        if (candidate.sameIdentityRegistryMapping) total += 10
        if (candidate.sameIp) total += 5
        if (candidate.sameDisplayName) total += 5

        if (candidate.conflictingWalletAddress) total -= 30
        if (candidate.conflictingPublicKey) total -= 30
        if (candidate.conflictingGlobalId) total -= 20

        if (candidate.sameDisplayName &&
            !candidate.sameWalletAddress &&
            !candidate.samePublicKey &&
            !candidate.sameGlobalId
        ) {
            total -= 15
        }

        if (candidate.sameIp &&
            !candidate.sameWalletAddress &&
            !candidate.samePublicKey &&
            !candidate.sameGlobalId
        ) {
            total -= 10
        }

        val clamped =
            total.coerceIn(0, 100)
        val category =
            when (clamped) {
                in 80..100 -> "high-confidence duplicate"
                in 60..79 -> "likely duplicate"
                in 40..59 -> "possible duplicate"
                in 20..39 -> "weak signal"
                else -> "unsafe/ambiguous"
            }

        return DedupRiskScore(
            score = clamped,
            category = category,
            candidate = candidate
        )
    }

    fun summarize(
        candidates: List<SoftDedupCandidate>
    ): DedupDryRunSummary {
        val scored =
            candidates.map { score(it) }

        return DedupDryRunSummary(
            totalCandidates = scored.size,
            highConfidenceCount = scored.count { it.category == "high-confidence duplicate" },
            likelyCount = scored.count { it.category == "likely duplicate" },
            possibleCount = scored.count { it.category == "possible duplicate" },
            weakCount = scored.count { it.category == "weak signal" },
            unsafeOrAmbiguousCount = scored.count { it.category == "unsafe/ambiguous" },
            topRiskyConflicts = scored.sortedBy { it.score }.take(3),
            topSafeCandidates = scored.sortedByDescending { it.score }.take(3)
        )
    }

    fun report(
        context: Context,
        limit: Int = 40
    ): String {
        val candidates =
            IdentityDedupReporter.candidates(context, limit)

        if (candidates.isEmpty()) {
            return "No dedup dry-run signals yet."
        }

        val scored =
            candidates.map { score(it) }
        val summary =
            summarize(candidates)

        return buildString {
            appendLine("DEDUP DRY-RUN REPORT")
            appendLine("======================")
            appendLine("total=${summary.totalCandidates} | high=${summary.highConfidenceCount} | likely=${summary.likelyCount} | possible=${summary.possibleCount} | weak=${summary.weakCount} | unsafe=${summary.unsafeOrAmbiguousCount}")
            appendLine()
            appendLine("TOP SAFE CANDIDATES")
            summary.topSafeCandidates.forEach { item ->
                val conflict =
                    IdentityConflictClassifier.fromDedupRisk(item)
                appendLine("${item.score} | ${item.category} | ${conflict.type} | ${conflict.severity} | ${conflict.suggestedAction} | ${item.candidate.reason} | ${item.candidate.leftLabel} <-> ${item.candidate.rightLabel}")
            }
            appendLine()
            appendLine("TOP RISKY CONFLICTS")
            summary.topRiskyConflicts.forEach { item ->
                val conflict =
                    IdentityConflictClassifier.fromDedupRisk(item)
                appendLine("${item.score} | ${item.category} | ${conflict.type} | ${conflict.severity} | ${conflict.suggestedAction} | ${item.candidate.reason} | ${item.candidate.leftLabel} <-> ${item.candidate.rightLabel}")
            }
            appendLine()
            appendLine("ALL SCORED CANDIDATES")
            scored.forEach { item ->
                val conflict =
                    IdentityConflictClassifier.fromDedupRisk(item)
                appendLine("${item.score} | ${item.category} | ${conflict.type} | ${conflict.severity} | ${conflict.suggestedAction} | ${item.candidate.reason} | ${item.candidate.leftLabel} <-> ${item.candidate.rightLabel}")
            }
        }.trim()
    }
}
