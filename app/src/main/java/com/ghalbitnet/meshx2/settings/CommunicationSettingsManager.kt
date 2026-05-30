package com.ghalbitnet.meshx2.settings

import android.content.Context

object CommunicationSettingsManager {
    private const val PREFS = "ghalbit_comm_settings"
    private const val KEY_TECHNICAL_DETAIL = "technical_detail"
    private const val KEY_VOICE_SAVER = "voice_saver"
    private const val KEY_EMERGENCY_PRIORITY = "emergency_priority"
    private const val KEY_AI_TRANSCRIPT = "ai_transcript"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isTechnicalDetailEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_TECHNICAL_DETAIL, false)
    fun setTechnicalDetailEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TECHNICAL_DETAIL, enabled).apply()
    }

    fun isVoiceSaverEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_VOICE_SAVER, true)
    fun setVoiceSaverEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VOICE_SAVER, enabled).apply()
    }

    fun isEmergencyPriorityEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_EMERGENCY_PRIORITY, true)
    fun setEmergencyPriorityEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_EMERGENCY_PRIORITY, enabled).apply()
    }

    fun isLocalAiTranscriptEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_AI_TRANSCRIPT, true)
    fun setLocalAiTranscriptEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AI_TRANSCRIPT, enabled).apply()
    }
}
