package com.ghalbitnet.meshx2.identity

object IdentityQualityReporter {

    fun score(identity: ResolvedPeerIdentity): IdentityQualityScore {
        var score = 0

        if (!identity.publicKey.isNullOrBlank()) score += 30
        if (!identity.globalId.isNullOrBlank()) score += 25
        if (!identity.walletAddress.isNullOrBlank()) score += 20
        if (identity.resolutionSource == "store") score += 10
        if (identity.resolutionSource == "registry") score += 10
        if (identity.peerIp.isNotBlank()) score += 5

        val hasOnlyLegacy =
            identity.publicKey.isNullOrBlank() &&
                identity.globalId.isNullOrBlank() &&
                identity.walletAddress.isNullOrBlank()
        if (hasOnlyLegacy) score -= 20
        if (identity.primaryLabel == "Unknown peer") score -= 15

        val clamped = score.coerceIn(0, 100)
        val label =
            when (clamped) {
                in 80..100 -> "strong"
                in 60..79 -> "good"
                in 40..59 -> "partial"
                in 20..39 -> "weak"
                else -> "legacy-only / unknown"
            }

        return IdentityQualityScore(
            score = clamped,
            label = label
        )
    }
}
