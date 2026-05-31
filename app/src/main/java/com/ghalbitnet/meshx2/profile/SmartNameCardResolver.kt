package com.ghalbitnet.meshx2.profile

import android.content.Context
import com.ghalbitnet.meshx2.activityfeed.ActivityFeedManager
import com.ghalbitnet.meshx2.activityfeed.ActivityFeedType

sealed class SmartNameCardResolveResult {
    data class Success(
        val profile: CommunityProfile,
        val source: String,
        val verified: Boolean
    ) : SmartNameCardResolveResult()

    data class Failed(
        val reason: String,
        val safePreview: String
    ) : SmartNameCardResolveResult()
}

object SmartNameCardResolver {

    fun resolve(
        context: Context,
        rawInput: String?,
        fallbackGlobalId: String? = null,
        fallbackName: String = "Belum dikenal",
        fallbackRouteHint: String? = null
    ): SmartNameCardResolveResult {
        val appContext = context.applicationContext
        ActivityFeedManager.bind(appContext)

        val normalized = rawInput?.let { ProfileSmartLink.extractPayloadFromIncomingText(it) }
        if (!normalized.isNullOrBlank()) {
            val payload = ProfileQrCodec.decode(normalized)
            if (payload != null) {
                val profile = ProfileSyncManager.applyScannedQr(context, payload)
                ActivityFeedManager.publish(
                    type = ActivityFeedType.PEER_VERIFIED,
                    title = "Kartu nama dibaca",
                    message = "Kartu nama ${profile.displayName} berhasil diterjemahkan dari payload.",
                    peerId = profile.globalId,
                    source = "SmartNameCardResolver",
                    metadata = "{\"source\":\"payload\",\"verified\":true}"
                )
                return SmartNameCardResolveResult.Success(
                    profile = profile,
                    source = "payload",
                    verified = true
                )
            }

            ActivityFeedManager.publish(
                type = ActivityFeedType.SECURITY_EVENT,
                title = "Kartu nama gagal dibaca",
                message = "Payload kartu nama tidak valid atau rusak.",
                source = "SmartNameCardResolver",
                metadata = "{\"source\":\"payload\",\"verified\":false}"
            )
            return SmartNameCardResolveResult.Failed(
                reason = "Payload kartu nama tidak valid atau rusak.",
                safePreview = normalized.take(120)
            )
        }

        if (!fallbackGlobalId.isNullOrBlank()) {
            val profile = ProfileRepository.getResolvedContact(
                context = context,
                globalId = fallbackGlobalId,
                chatId = fallbackGlobalId,
                fallbackDisplayName = fallbackName,
                publicKeyHash = null,
                routeHint = fallbackRouteHint
            )
            ActivityFeedManager.publish(
                type = ActivityFeedType.RUNTIME_EVENT,
                title = "Kartu nama lokal dibuka",
                message = "Menampilkan kartu nama dari data lokal untuk $fallbackGlobalId.",
                peerId = fallbackGlobalId,
                source = "SmartNameCardResolver",
                metadata = "{\"source\":\"localFallback\",\"verified\":false}"
            )
            return SmartNameCardResolveResult.Success(
                profile = profile,
                source = "localFallback",
                verified = false
            )
        }

        ActivityFeedManager.publish(
            type = ActivityFeedType.SECURITY_EVENT,
            title = "Kartu nama kosong",
            message = "Tidak ada payload atau Global ID yang bisa diterjemahkan.",
            source = "SmartNameCardResolver"
        )
        return SmartNameCardResolveResult.Failed(
            reason = "Tidak ada payload atau Global ID yang bisa diterjemahkan.",
            safePreview = rawInput?.take(120).orEmpty()
        )
    }
}
