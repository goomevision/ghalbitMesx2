package com.ghalbitnet.meshx2.verified

/**
 * PHASE 251M
 * Payload used when exporting/sharing cards to WhatsApp, Telegram, Email, etc.
 */
data class VerifiedCardSharePayload(
    val title: String,
    val summary: String,
    val verificationText: String,
    val qrContent: String,
    val shareFormat: CrossPlatformShareFormat
)
