package com.ghalbitnet.meshx2.identity

object ShadowMappingAggregator {

    fun summarize(
        mappings: List<ShadowCanonicalMapping>
    ): ShadowMappingSummary {
        if (mappings.isEmpty()) {
            return ShadowMappingSummary(
                totalMappings = 0,
                highConfidenceCount = 0,
                mediumConfidenceCount = 0,
                lowConfidenceCount = 0,
                conflictedCount = 0,
                unknownCount = 0,
                averageConfidence = 0
            )
        }

        return ShadowMappingSummary(
            totalMappings = mappings.size,
            highConfidenceCount = mappings.count { it.confidence >= 80 && it.riskLevel != "conflicted" },
            mediumConfidenceCount = mappings.count { it.confidence in 50..79 && it.riskLevel != "conflicted" },
            lowConfidenceCount = mappings.count { it.confidence in 1..49 && it.riskLevel != "conflicted" },
            conflictedCount = mappings.count { it.riskLevel == "conflicted" || it.riskLevel == "high" },
            unknownCount = mappings.count { it.confidence == 0 || it.riskLevel == "unknown" },
            averageConfidence = mappings.map { it.confidence }.average().toInt()
        )
    }
}
