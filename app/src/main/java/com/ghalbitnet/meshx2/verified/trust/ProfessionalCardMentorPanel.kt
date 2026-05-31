package com.ghalbitnet.meshx2.verified.trust

object ProfessionalCardMentorPanel {

    fun render(summary: ProfessionalCardTrustSummary): String {
        return """
            Mentor:
            ${summary.mentorLevel}
        """.trimIndent()
    }
}
