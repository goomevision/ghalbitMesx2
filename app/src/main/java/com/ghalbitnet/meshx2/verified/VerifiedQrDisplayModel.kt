package com.ghalbitnet.meshx2.verified

/**
 * PHASE 251K
 * UI model for QR generation and display.
 */
data class VerifiedQrDisplayModel(
    val encodedPayload: String,
    val label: String,
    val verificationHint: String
) {
    companion object {
        fun fromPayload(payload: QrVerificationPayload): VerifiedQrDisplayModel {
            return VerifiedQrDisplayModel(
                encodedPayload = listOf(
                    payload.type,
                    payload.documentId,
                    payload.hash
                ).joinToString("|"),
                label = "GHALBIT Verification QR",
                verificationHint = "Scan to verify document authenticity"
            )
        }
    }
}
