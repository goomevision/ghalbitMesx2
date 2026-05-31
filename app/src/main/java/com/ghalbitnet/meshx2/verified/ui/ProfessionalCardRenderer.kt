package com.ghalbitnet.meshx2.verified.ui

object ProfessionalCardRenderer {
    fun render(model: ProfessionalCardUiModel): String {
        return buildString {
            appendLine("GHALBIT VERIFIED CARD")
            appendLine(model.displayName)
            appendLine(model.role)
            appendLine(model.community)
            appendLine("Trust Score: ${model.trustScore}")
            appendLine(if (model.verified) "VERIFIED" else "UNVERIFIED")
        }
    }
}
