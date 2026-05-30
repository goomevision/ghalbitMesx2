package com.ghalbitnet.meshx2.identity

object IdentityQualityAggregator {

    fun summarize(identities: List<ResolvedPeerIdentity>): IdentityQualitySummary {
        if (identities.isEmpty()) {
            return IdentityQualitySummary(
                totalIdentities = 0,
                strongCount = 0,
                goodCount = 0,
                partialCount = 0,
                weakCount = 0,
                legacyOnlyOrUnknownCount = 0,
                averageScore = 0,
                lowestScore = 0,
                highestScore = 0
            )
        }

        val scored =
            identities.map { IdentityQualityReporter.score(it) }

        return IdentityQualitySummary(
            totalIdentities = identities.size,
            strongCount = scored.count { it.label == "strong" },
            goodCount = scored.count { it.label == "good" },
            partialCount = scored.count { it.label == "partial" },
            weakCount = scored.count { it.label == "weak" },
            legacyOnlyOrUnknownCount = scored.count { it.label == "legacy-only / unknown" },
            averageScore = scored.map { it.score }.average().toInt(),
            lowestScore = scored.minOf { it.score },
            highestScore = scored.maxOf { it.score }
        )
    }
}
