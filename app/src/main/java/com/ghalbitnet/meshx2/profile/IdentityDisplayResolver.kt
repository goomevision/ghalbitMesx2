package com.ghalbitnet.meshx2.profile

import android.content.Context
import android.util.Log

data class IdentityDisplay(
    val title: String,
    val subtitle: String,
    val detail: String,
    val globalId: String?,
    val publicKeyHash: String?,
    val isSaved: Boolean,
    val hasPublicProfile: Boolean
)

object IdentityDisplayResolver {
    fun resolve(
        context: Context,
        globalId: String?,
        chatId: String?,
        fallbackName: String?,
        publicKeyHash: String? = null,
        routeHint: String? = null
    ): IdentityDisplay {
        val safeFallback = fallbackName?.takeIf { it.isNotBlank() }
            ?: globalId?.takeIf { it.isNotBlank() }
            ?: chatId?.takeIf { it.isNotBlank() }
            ?: "Pengguna GhalbitNet"
        val profile = ProfileRepository.getResolvedContact(
            context = context,
            globalId = globalId,
            chatId = chatId,
            fallbackDisplayName = safeFallback,
            publicKeyHash = publicKeyHash,
            routeHint = routeHint
        )
        val title = profile.localAlias?.takeIf { it.isNotBlank() }
            ?: profile.savedAsName?.takeIf { it.isNotBlank() }
            ?: profile.displayName.takeIf { it.isNotBlank() }
            ?: profile.nickname.takeIf { it.isNotBlank() }
            ?: safeFallback
        val subtitle = profile.roleTitle.takeIf { it.isNotBlank() }
            ?: profile.publicSubtitle.takeIf { it.isNotBlank() && it != title }
            ?: profile.communityName.takeIf { it.isNotBlank() }
            ?: "Kontak GhalbitNet"
        val detail = listOfNotNull(
            profile.communityLabel?.takeIf { it.isNotBlank() },
            profile.communityName.takeIf { it.isNotBlank() },
            profile.region.takeIf { profile.isRegionVisible && it.isNotBlank() }
        ).distinct().joinToString(" • ").ifBlank { "Tap nama untuk melihat kartu profil" }
        val display = IdentityDisplay(
            title = title,
            subtitle = subtitle,
            detail = detail,
            globalId = profile.globalId.takeIf { it.isNotBlank() },
            publicKeyHash = profile.publicKeyHash.takeIf { it.isNotBlank() },
            isSaved = !profile.localAlias.isNullOrBlank() || !profile.savedAsName.isNullOrBlank() || profile.isFavorite || profile.isPinned,
            hasPublicProfile = profile.isPublicProfile || profile.profileVersion > 0 || profile.signature.isNotBlank()
        )
        Log.d("GHALBIT-IDENTITY", "resolved title=${display.title} saved=${display.isSaved} profile=${display.hasPublicProfile}")
        return display
    }

    fun compactUnknown(value: String?): String {
        val raw = value?.takeIf { it.isNotBlank() } ?: return "Pengguna GhalbitNet"
        return if (raw.length > 14) raw.take(6) + "…" + raw.takeLast(4) else raw
    }
}
