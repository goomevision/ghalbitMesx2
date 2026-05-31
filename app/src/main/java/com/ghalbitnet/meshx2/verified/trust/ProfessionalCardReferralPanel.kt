package com.ghalbitnet.meshx2.verified.trust

object ProfessionalCardReferralPanel {

    fun render(summary: ProfessionalCardTrustSummary): String {
        return """
            Referral:
            ${summary.referralLabel}
        """.trimIndent()
    }
}
