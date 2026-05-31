package com.ghalbitnet.meshx2.verified.export

import com.ghalbitnet.meshx2.verified.ui.ProfessionalCardUiModel

object CardExportManager {
    fun pngFile(model: ProfessionalCardUiModel): String {
        return ProfessionalPngRenderer.buildFileName(model)
    }

    fun pdfFile(model: ProfessionalCardUiModel): String {
        return PdfExportRenderer.buildFileName(model)
    }
}
