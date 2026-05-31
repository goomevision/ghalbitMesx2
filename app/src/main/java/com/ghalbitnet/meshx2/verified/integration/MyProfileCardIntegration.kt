package com.ghalbitnet.meshx2.verified.integration

import com.ghalbitnet.meshx2.verified.ui.ProfessionalCardUiModel

object MyProfileCardIntegration {
    fun buildPreviewModel(
        globalId: String,
        displayName: String,
        role: String,
        community: String,
        trustScore: Int,
        verified: Boolean,
        profilePhotoUri: String? = null
    ): ProfessionalCardUiModel {
        return ProfessionalCardUiModel(
            globalId = globalId,
            displayName = displayName,
            role = role,
            community = community,
            trustScore = trustScore,
            verified = verified,
            profilePhotoUri = profilePhotoUri
        )
    }
}
