package com.ghalbitnet.meshx2.online

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.chat.ChatDeliveryManager

object GhalbitStartupScheduler {
    fun schedule(context: Context) {
        GhalbitBackgroundSyncWorker.schedule(context)
        ChatDeliveryManager.bind(context.applicationContext)
        Log.d("GHALBIT-BOOT", "sync scheduled")
    }
}
