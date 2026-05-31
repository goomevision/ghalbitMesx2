package com.ghalbitnet.meshx2.profile

import android.net.Uri

object ProfileSmartLink {
    private const val CENTRAL_BASE_URL = "https://ghalbit.net/card"
    private const val LOCAL_BASE_URL = "http://127.0.0.1:8787/card"

    fun central(profile: CommunityProfile): String {
        return "$CENTRAL_BASE_URL/${Uri.encode(profile.globalId)}"
    }

    fun local(profile: CommunityProfile): String {
        return "$LOCAL_BASE_URL/${Uri.encode(profile.globalId)}"
    }

    fun appDeepLink(profile: CommunityProfile, encodedPayload: String): String {
        return Uri.Builder()
            .scheme("ghalbit")
            .authority("card")
            .appendQueryParameter("id", profile.globalId)
            .appendQueryParameter("payload", encodedPayload)
            .build()
            .toString()
    }

    fun extractPayloadFromIncomingText(text: String): String {
        val markedPayload = ProfileShareFormatter.extractPayload(text)
        if (markedPayload != text.trim()) return markedPayload

        val appLink = Regex("ghalbit://card\\?[^\\s]+")
            .find(text)
            ?.value
        if (!appLink.isNullOrBlank()) {
            return runCatching {
                Uri.parse(appLink).getQueryParameter("payload").orEmpty()
            }.getOrDefault("").ifBlank { text.trim() }
        }

        return text.trim()
    }
}
