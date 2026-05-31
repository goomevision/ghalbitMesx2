package com.ghalbitnet.meshx2.verified

/**
 * PHASE 251D
 * Export configuration for future image/pdf generation.
 */
data class VerifiedCardExportSpec(
    val widthPx: Int = 1080,
    val heightPx: Int = 1350,
    val includeQr: Boolean = true,
    val includeSignatureStatus: Boolean = true,
    val includeVerificationUrls: Boolean = false,
    val filePrefix: String = "ghalbit_verified_card"
)
