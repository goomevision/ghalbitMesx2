package com.ghalbitnet.meshx2.verified

object VerifiedCardShareBuilder {

    fun buildTextCard(payload: VerifiedNameCardPayload): String {
        return buildString {
            appendLine("GHALBITNET VERIFIED CARD")
            appendLine()
            appendLine("Name      : ${payload.displayName}")
            appendLine("Role      : ${payload.role ?: "-"}")
            appendLine("Community : ${payload.community ?: "-"}")
            appendLine("Global ID : ${payload.globalId}")
            appendLine()
            appendLine("Verification available via QR and digital signature")
        }
    }

    fun buildSharePayload(
        payload: VerifiedNameCardPayload,
        qr: VerifiedQrDisplayModel
    ): VerifiedCardSharePayload {
        return VerifiedCardSharePayload(
            title = payload.displayName,
            summary = buildTextCard(payload),
            verificationText = "Digitally signed GHALBITNET identity card",
            qrContent = qr.encodedPayload,
            shareFormat = CrossPlatformShareFormat.TEXT
        )
    }
}
