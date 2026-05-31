package com.ghalbitnet.meshx2.verified.trust

object ProfessionalCardReputationPanel {

    fun render(summary: ProfessionalCardTrustSummary): String {
        return "Community Reputation : ${summary.communityReputation}"
    }
}
