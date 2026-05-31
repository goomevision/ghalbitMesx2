package com.ghalbitnet.meshx2.verified

/**
 * PHASE 251F
 * UI-ready model for showing a professional verified card in chat/contact screens.
 */
data class VerifiedCardUiModel(
    val title: String,
    val primaryName: String,
    val subtitle: String,
    val community: String,
    val globalId: String,
    val statusLabel: String,
    val qrPayloadText: String? = null,
    val actionChatEnabled: Boolean = true,
    val actionCallEnabled: Boolean = true,
    val actionSaveEnabled: Boolean = true
) {
    companion object {
        fun fromNameCard(
            payload: VerifiedNameCardPayload,
            qrPayloadText: String? = null
        ): VerifiedCardUiModel {
            val status = when {
                payload.isExpired() -> "EXPIRED"
                payload.isOfflineVerifiable() -> "VERIFIED"
                else -> "PENDING"
            }

            return VerifiedCardUiModel(
                title = "GHALBITNET VERIFIED CARD",
                primaryName = payload.displayName.ifBlank { "Unknown" },
                subtitle = payload.role ?: "Community Member",
                community = payload.community ?: "GHALBITNET",
                globalId = payload.globalId,
                statusLabel = status,
                qrPayloadText = qrPayloadText
            )
        }
    }
}
