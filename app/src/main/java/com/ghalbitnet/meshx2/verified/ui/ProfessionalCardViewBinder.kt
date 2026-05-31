package com.ghalbitnet.meshx2.verified.ui

object ProfessionalCardViewBinder {
    fun buildSummary(model: ProfessionalCardUiModel): String {
        return "${model.displayName} (${model.role}) - ${model.community}"
    }
}
