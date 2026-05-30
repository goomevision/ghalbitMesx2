package com.ghalbitnet.meshx2.settings

import android.content.Context
import android.util.Log

object DeveloperModeManager {
    private const val PREFS = "ghalbit_developer_mode"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        Log.d("GHALBIT-DEBUG", "developer mode ${if (enabled) "enabled" else "disabled"}")
    }
}
