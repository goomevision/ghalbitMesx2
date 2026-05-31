package com.ghalbitnet.meshx2.verified

/**
 * PHASE 251O
 * QR bitmap specification used by the future QR generator.
 */
data class VerifiedQrBitmapSpec(
    val content: String,
    val sizePx: Int = 768,
    val marginPx: Int = 24,
    val errorCorrectionLevel: String = "M"
) {
    companion object {
        fun fromDisplayModel(model: VerifiedQrDisplayModel): VerifiedQrBitmapSpec {
            return VerifiedQrBitmapSpec(content = model.encodedPayload)
        }
    }
}
