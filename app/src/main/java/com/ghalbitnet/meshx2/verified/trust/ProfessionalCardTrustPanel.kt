package com.ghalbitnet.meshx2.verified.trust

object ProfessionalCardTrustPanel {

    fun render(summary: ProfessionalCardTrustSummary): String {
        return """
            Trust Score : ${summary.trustScore}
            Rank        : ${summary.trustRank}
        """.trimIndent()
    }
}
