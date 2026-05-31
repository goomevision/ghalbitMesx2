package com.ghalbitnet.meshx2.verified

/**
 * PHASE 251I
 * Android View state for a professional verified card screen/component.
 *
 * This state is UI-framework safe and can later be consumed by XML View,
 * RecyclerView item, or a custom card component.
 */
data class VerifiedCardViewState(
    val headerText: String,
    val nameText: String,
    val roleText: String,
    val communityText: String,
    val globalIdText: String,
    val statusText: String,
    val showVerifiedBadge: Boolean,
    val showQrArea: Boolean,
    val chatButtonText: String = "Chat",
    val callButtonText: String = "Call",
    val saveButtonText: String = "Save"
) {
    companion object {
        fun fromUiModel(model: VerifiedCardUiModel): VerifiedCardViewState {
            return VerifiedCardViewState(
                headerText = model.title,
                nameText = model.primaryName,
                roleText = model.subtitle,
                communityText = model.community,
                globalIdText = model.globalId,
                statusText = model.statusLabel,
                showVerifiedBadge = model.statusLabel == "VERIFIED",
                showQrArea = !model.qrPayloadText.isNullOrBlank()
            )
        }
    }
}
