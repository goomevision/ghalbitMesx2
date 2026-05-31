package com.ghalbitnet.meshx2.verified.screen

import com.ghalbitnet.meshx2.verified.ui.ProfessionalCardUiModel

data class CardPreviewScreen(
    val model: ProfessionalCardUiModel,
    val showQr: Boolean = true,
    val allowShare: Boolean = true
)
