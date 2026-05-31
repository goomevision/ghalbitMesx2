package com.ghalbitnet.meshx2.verified

/**
 * PHASE 251G
 * Template metadata for future PNG/JPG export rendering.
 */
data class VerifiedCardImageTemplate(
    val templateName: String = "professional_card_v1",
    val showLogo: Boolean = true,
    val showQr: Boolean = true,
    val showVerificationBadge: Boolean = true,
    val showGlobalId: Boolean = true,
    val showCommunity: Boolean = true
)
