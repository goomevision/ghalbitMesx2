package com.ghalbitnet.meshx2.verified

data class VerifiedCardPngExportPlan(
    val fileNamePrefix: String = "ghalbit_verified_card",
    val includeQr: Boolean = true,
    val includeVerificationBadge: Boolean = true,
    val includeTimestamp: Boolean = true
)
