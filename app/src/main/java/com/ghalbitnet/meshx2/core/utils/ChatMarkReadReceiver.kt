package com.ghalbitnet.meshx2.core.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ChatMarkReadReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val peerName =
            intent.getStringExtra(AppNotificationManager.EXTRA_PEER_NAME)
                .orEmpty()

        if (peerName.isBlank()) {
            return
        }

        AppNotificationManager.clearChatNotifications(context, peerName)
    }
}
