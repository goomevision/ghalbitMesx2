package com.ghalbitnet.meshx2.verified.export

import com.ghalbitnet.meshx2.verified.ui.ProfessionalCardUiModel

object ProfessionalPngRenderer {
    fun buildFileName(model: ProfessionalCardUiModel): String {
        return "verified_card_${model.globalId}.png"
    }
}
