package com.ghalbitnet.meshx2.core.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ghalbitnet.meshx2.chat.ChatDeliveryManager

class ChatMarkReadReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val peerName =
            intent.getStringExtra(AppNotificationManager.EXTRA_PEER_NAME)
                .orEmpty()
        val peerGlobalId =
            intent.getStringExtra(AppNotificationManager.EXTRA_PEER_GLOBAL_ID)

        if (peerName.isBlank()) {
            return
        }

        AppNotificationManager.clearChatNotifications(context, peerName)
        ChatDeliveryManager.markChatReadRemotely(context, peerName, peerGlobalId)
        Log.d("GHALBIT-READ", "remote receipt sent conversation=$peerName")
    }
}
