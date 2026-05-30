package com.ghalbitnet.meshx2.profile

import android.content.Context
import android.util.Log

object ContactDisplayResolver {
    fun resolveName(
        context: Context,
        globalId: String?,
        chatId: String?,
        publicDisplayName: String?,
        publicNickname: String?
    ): String {
        val resolved = ProfileRepository.getResolvedContact(context, globalId, chatId, publicDisplayName ?: publicNickname ?: chatId ?: "Kontak")
        val name =
            when {
                resolved.primaryName.isNotBlank() && resolved.primaryName != resolved.displayName -> {
                    Log.d("GHALBIT-CONTACT-DISPLAY", "resolved localAlias")
                    resolved.primaryName
                }
                resolved.displayName.isNotBlank() -> {
                    Log.d("GHALBIT-CONTACT-DISPLAY", "resolved publicName")
                    resolved.displayName
                }
                !publicNickname.isNullOrBlank() -> publicNickname
                !globalId.isNullOrBlank() -> {
                    Log.d("GHALBIT-CONTACT-DISPLAY", "fallback shortId")
                    globalId.take(10)
                }
                else -> chatId ?: "Kontak"
            }
        return name
    }
}
