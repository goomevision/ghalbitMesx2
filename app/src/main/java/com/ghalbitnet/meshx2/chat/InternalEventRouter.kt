package com.ghalbitnet.meshx2.chat

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.profile.ContactDisplayResolver
import com.ghalbitnet.meshx2.settings.DeveloperModeManager

object InternalEventRouter {
    fun toChatMessage(
        context: Context,
        packetId: String,
        chatId: String,
        senderName: String,
        type: String,
        payload: String,
        isSent: Boolean,
        status: String,
        senderGlobalId: String? = null,
        publicDisplayName: String? = null,
        publicNickname: String? = null
    ): ChatMessage? {
        val decision = InternalSignalFilter.classify(type, payload)
        if (!decision.hidden) {
            return null
        }
        val displayName =
            ContactDisplayResolver.resolveName(
                context = context,
                globalId = senderGlobalId,
                chatId = chatId,
                publicDisplayName = publicDisplayName ?: senderName,
                publicNickname = publicNickname
            )
        val developerMode = DeveloperModeManager.isEnabled(context)
        val humanEvent = HumanEventFormatter.format(type, decision.humanText ?: "Event internal")
        if (!developerMode && humanEvent == null) {
            return ChatMessage(
                packetId = packetId,
                chatId = chatId,
                senderName = displayName,
                content = "",
                contentType = "CALL_EVENT",
                messageType = decision.messageType.name,
                visibilityType = MessageVisibility.HIDDEN.name,
                internalSignalType = decision.internalSignalType ?: type.uppercase(),
                isSent = isSent,
                status = status
            )
        }
        val content =
            if (developerMode) {
                buildString {
                    append(humanEvent ?: decision.humanText ?: "Event internal")
                    decision.debugText?.takeIf { it.isNotBlank() }?.let {
                        append("\nDEBUG: ")
                        append(it)
                    }
                }
            } else {
                humanEvent ?: decision.humanText ?: "Event panggilan"
            }
        Log.d("GHALBIT-SIGNAL", "routed runtime type=${decision.internalSignalType ?: type}")
        Log.d("GHALBIT-CALL-UX", "human event shown state=${decision.internalSignalType ?: type}")
        return ChatMessage(
            packetId = packetId,
            chatId = chatId,
            senderName = displayName,
            content = content,
            contentType = "CALL_EVENT",
            messageType = MessageVisibility.SYSTEM_EVENT.name,
            visibilityType = MessageVisibility.VISIBLE.name,
            internalSignalType = decision.internalSignalType ?: type.uppercase(),
            isSent = isSent,
            status = status
        )
    }
}
