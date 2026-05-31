package com.ghalbitnet.meshx2.verified

object ExternalShareAdapter {
    fun buildShareText(card: VerifiedNameCardPayload, verifyLink: UniversalVerifyLink): String {
        return buildString {
            appendLine("GHALBITNET VERIFIED CARD")
            appendLine(card.displayName)
            appendLine(card.globalId)
            appendLine()
            appendLine("Verify:")
            appendLine(verifyLink.toUrl())
        }
    }
}
