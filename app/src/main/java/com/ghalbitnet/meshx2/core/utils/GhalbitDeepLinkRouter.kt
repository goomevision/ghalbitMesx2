package com.ghalbitnet.meshx2.core.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import com.ghalbitnet.meshx2.call.CallSessionActivity
import com.ghalbitnet.meshx2.chat.ChatActivity

object GhalbitDeepLinkRouter {
    const val EXTRA_OPEN_MODE = "openMode"
    const val MODE_CHAT_MESSAGE = "CHAT_MESSAGE"
    const val MODE_INCOMING_CALL = "INCOMING_CALL"
    const val EXTRA_CONVERSATION_ID = "conversationId"
    const val EXTRA_MESSAGE_ID = "messageId"
    const val EXTRA_CALL_ID = "callId"
    const val EXTRA_CALLER_GLOBAL_ID = "callerGlobalId"
    const val EXTRA_CALLER_NAME = "callerName"
    const val EXTRA_ROUTE_TYPE = "routeType"
    const val EXTRA_CALL_ACTION = "callAction"
    const val ACTION_ACCEPT_CALL = "com.ghalbitnet.meshx2.action.ACCEPT_CALL"
    const val ACTION_REJECT_CALL = "com.ghalbitnet.meshx2.action.REJECT_CALL"
    const val ACTION_OPEN_CALL = "com.ghalbitnet.meshx2.action.OPEN_CALL"

    fun chatIntent(
        context: Context,
        conversationId: String,
        senderGlobalId: String? = null,
        senderPublicKey: String? = null,
        senderWalletAddress: String? = null,
        senderDisplayName: String? = null,
        messageId: String? = null
    ): Intent {
        return Intent(context, ChatActivity::class.java).apply {
            putExtra(EXTRA_OPEN_MODE, MODE_CHAT_MESSAGE)
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
            putExtra(EXTRA_MESSAGE_ID, messageId)
            putExtra("peerName", conversationId)
            putExtra("peerIp", "")
            putExtra("peerGlobalId", senderGlobalId)
            putExtra("peerPublicKey", senderPublicKey)
            putExtra("peerWalletAddress", senderWalletAddress)
            putExtra("peerDisplayName", senderDisplayName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    }

    fun callIntent(
        context: Context,
        peerName: String,
        peerIp: String,
        callId: String,
        peerGlobalId: String? = null,
        peerPublicKey: String? = null,
        peerWalletAddress: String? = null,
        peerDisplayName: String? = null,
        action: String = ACTION_OPEN_CALL
    ): Intent {
        return CallSessionActivity.createIntent(
            context = context,
            peerName = peerName,
            peerIp = peerIp,
            callId = callId,
            incoming = true,
            peerGlobalId = peerGlobalId,
            peerPublicKey = peerPublicKey,
            peerWalletAddress = peerWalletAddress,
            peerDisplayName = peerDisplayName
        ).apply {
            putExtra(EXTRA_OPEN_MODE, MODE_INCOMING_CALL)
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CALLER_GLOBAL_ID, peerGlobalId)
            putExtra(EXTRA_CALLER_NAME, peerName)
            putExtra(EXTRA_ROUTE_TYPE, peerIp)
            putExtra(EXTRA_CALL_ACTION, action)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    }

    fun logChatOpen(conversationId: String) {
        Log.d("GHALBIT-DEEPLINK", "open chat conversationId=$conversationId")
    }

    fun logCallOpen(callId: String) {
        Log.d("GHALBIT-DEEPLINK", "open call callId=$callId")
    }
}
