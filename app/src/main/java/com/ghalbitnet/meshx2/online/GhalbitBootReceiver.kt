package com.ghalbitnet.meshx2.online

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class GhalbitBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d("GHALBIT-BOOT", "receiver triggered")
            GhalbitStartupScheduler.schedule(context.applicationContext)
            Log.d("GHALBIT-BOOT", "pending restored")
        }
    }
}
