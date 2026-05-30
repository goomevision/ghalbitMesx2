package com.ghalbitnet.meshx2.identity

object RoutingIdentityAggregator {

    fun summarize(
        identities: List<RoutingShadowIdentity>
    ): RoutingIdentitySummary {
        if (identities.isEmpty()) {
            return RoutingIdentitySummary(
                totalRoutesInspected = 0,
                canonicalReadyCount = 0,
                mixedCount = 0,
                conflictedCount = 0,
                legacyOnlyCount = 0,
                averageConfidence = 0
            )
        }

        return RoutingIdentitySummary(
            totalRoutesInspected = identities.size,
            canonicalReadyCount = identities.count {
                !it.routeOwnerGlobalId.isNullOrBlank() &&
                    !it.routeOwnerPublicKey.isNullOrBlank() &&
                    it.riskLevel == "low"
            },
            mixedCount = identities.count {
                (!it.routeOwnerGlobalId.isNullOrBlank() || !it.routeOwnerPublicKey.isNullOrBlank()) &&
                    it.riskLevel == "medium"
            },
            conflictedCount = identities.count {
                it.riskLevel == "high" || it.riskLevel == "unknown"
            },
            legacyOnlyCount = identities.count {
                it.routeOwnerGlobalId.isNullOrBlank() && it.routeOwnerPublicKey.isNullOrBlank()
            },
            averageConfidence = identities.map { it.confidence }.average().toInt()
        )
    }
}
