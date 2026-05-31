package com.ghalbitnet.meshx2.verified

/**
 * PHASE 256A
 * Universal verification link for external apps such as WhatsApp, Telegram, Email, and browsers.
 */
data class UniversalVerifyLink(
    val documentType: String,
    val documentId: String,
    val hash: String,
    val baseUrl: String = "https://verify.ghalbit.net"
) {
    fun toUrl(): String =
        "$baseUrl/verify?type=$documentType&id=$documentId&hash=$hash"
}
