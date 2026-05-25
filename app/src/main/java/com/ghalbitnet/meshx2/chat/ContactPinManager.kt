package com.ghalbitnet.meshx2.chat

import android.content.Context

object ContactPinManager {
    private const val PREFS_NAME = "contact_pin_state"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isPinned(
        context: Context,
        peerName: String
    ): Boolean = prefs(context).getBoolean(peerName, false)

    fun setPinned(
        context: Context,
        peerName: String,
        pinned: Boolean
    ) {
        prefs(context).edit().putBoolean(peerName, pinned).apply()
    }

    fun togglePinned(
        context: Context,
        peerName: String
    ): Boolean {
        val next = !isPinned(context, peerName)
        setPinned(context, peerName, next)
        return next
    }
}
