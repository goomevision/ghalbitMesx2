package com.ghalbitnet.meshx2.verified.ui

object ProfessionalCardViewBinder2 {
    fun buildSummary(model: ProfessionalCardUiModel): String {
        return "${model.displayName} | ${model.role} | Trust ${model.trustScore}"
    }
}
