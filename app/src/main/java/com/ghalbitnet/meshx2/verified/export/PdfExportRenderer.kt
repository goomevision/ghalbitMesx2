package com.ghalbitnet.meshx2.verified.export

import com.ghalbitnet.meshx2.verified.ui.ProfessionalCardUiModel

object PdfExportRenderer {
    fun buildFileName(model: ProfessionalCardUiModel): String {
        return "verified_card_${model.globalId}.pdf"
    }
}
