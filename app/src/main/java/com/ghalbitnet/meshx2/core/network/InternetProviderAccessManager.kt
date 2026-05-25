package com.ghalbitnet.meshx2.core.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ghalbitnet.meshx2.settings.HotspotVerificationManager
import com.ghalbitnet.meshx2.settings.WifiHotspotQrProofManager

object InternetProviderAccessManager {

    private const val PREFS_NAME = "internet_provider_access"
    private const val KEY_CONSENT = "consent"
    private const val KEY_SSID = "ssid"
    private const val KEY_PASSWORD = "password"
    private const val KEY_UPDATED_AT = "updated_at"

    data class ProviderProfile(
        val consentGiven: Boolean,
        val hotspotSsid: String,
        val hotspotPassword: String,
        val updatedAt: Long
    ) {
        val credentialsReady: Boolean
            get() = hotspotSsid.isNotBlank() && hotspotPassword.isNotBlank()

        fun maskedPassword(): String {
            if (hotspotPassword.isBlank()) {
                return "-"
            }
            return "*".repeat(hotspotPassword.length.coerceAtMost(12))
        }

        fun isConventionAligned(globalId: String): Boolean {
            return InternetProviderConventionManager.isAligned(
                hotspotSsid = hotspotSsid,
                hotspotPassword = hotspotPassword,
                globalId = globalId
            )
        }
    }

    fun snapshot(context: Context): ProviderProfile {
        val prefs = prefs(context)
        return ProviderProfile(
            consentGiven = prefs.getBoolean(KEY_CONSENT, false),
            hotspotSsid = prefs.getString(KEY_SSID, "").orEmpty(),
            hotspotPassword = prefs.getString(KEY_PASSWORD, "").orEmpty(),
            updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L)
        )
    }

    fun save(
        context: Context,
        consentGiven: Boolean,
        hotspotSsid: String,
        hotspotPassword: String
    ) {
        prefs(context)
            .edit()
            .putBoolean(KEY_CONSENT, consentGiven)
            .putString(KEY_SSID, hotspotSsid.trim())
            .putString(KEY_PASSWORD, hotspotPassword.trim())
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
        HotspotVerificationManager.invalidate(context)
        WifiHotspotQrProofManager.invalidate(context)
    }

    private fun prefs(context: Context): SharedPreferences {
        val masterKey =
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
