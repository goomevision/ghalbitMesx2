package com.ghalbitnet.meshx2.vpn

import android.content.Context

enum class VpnOperatingMode {
    MONITORING_PASSIVE,
    MONITORING_LIGHT,
    MONITORING_ONLY,
    ENFORCEMENT;

    companion object {
        private const val PREFS_NAME = "ghalbit_vpn_operating_mode"
        private const val KEY_MODE = "mode"

        fun current(context: Context): VpnOperatingMode {
            val raw =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_MODE, MONITORING_PASSIVE.name)
                    .orEmpty()
            return entries.firstOrNull { it.name == raw } ?: MONITORING_PASSIVE
        }

        fun set(
            context: Context,
            mode: VpnOperatingMode
        ) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MODE, mode.name)
                .apply()
        }
    }
}
