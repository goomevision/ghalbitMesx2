package com.ghalbitnet.meshx2.verified

/**
 * PHASE 251J
 * Chat representation for verified cards.
 */
data class VerifiedCardChatBubble(
    val messageType: String = "verified_card",
    val previewTitle: String,
    val previewSubtitle: String,
    val statusLabel: String,
    val globalId: String
) {
    companion object {
        fun fromCard(card: VerifiedCardUiModel): VerifiedCardChatBubble {
            return VerifiedCardChatBubble(
                previewTitle = card.primaryName,
                previewSubtitle = card.subtitle,
                statusLabel = card.statusLabel,
                globalId = card.globalId
            )
        }
    }
}
