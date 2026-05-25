package com.ghalbitnet.meshx2.settings

import android.content.Context
import com.ghalbitnet.meshx2.core.network.HotspotSystemConfigManager
import com.ghalbitnet.meshx2.core.network.InternetProviderAccessManager

object HotspotVerificationManager {

    private const val PREFS_NAME = "hotspot_verification"
    private const val KEY_VERIFIED_SIGNATURE = "verified_signature"

    enum class Requirement {
        HOTSPOT_OFF,
        PROFILE_MISSING,
        SYSTEM_MISMATCH,
        QR_PROOF_REQUIRED,
        PROFILE_UNVERIFIED
    }

    fun currentRequirement(context: Context): Requirement? {
        val onboarding = OnboardingManager.snapshot(context)
        if (!onboarding.completed) {
            return null
        }

        val readiness = com.ghalbitnet.meshx2.core.network.InternetProviderReadinessManager.snapshot(context)
        if (!readiness.hotspotActive) {
            return Requirement.HOTSPOT_OFF
        }

        val profile = InternetProviderAccessManager.snapshot(context)
        if (!profile.credentialsReady) {
            return Requirement.PROFILE_MISSING
        }

        val systemConfig = HotspotSystemConfigManager.snapshot(context)
        if (
            systemConfig.readable &&
                (
                    systemConfig.ssid?.trim() != profile.hotspotSsid.trim() ||
                        systemConfig.password?.trim() != profile.hotspotPassword.trim()
                    )
        ) {
            return Requirement.SYSTEM_MISMATCH
        }

        if (
            HotspotQrCapabilityManager.isQrProofRequired() &&
                !WifiHotspotQrProofManager.hasValidProof(
                    context,
                    profile.hotspotSsid,
                    profile.hotspotPassword
                )
        ) {
            return Requirement.QR_PROOF_REQUIRED
        }

        val verifiedSignature = prefs(context).getString(KEY_VERIFIED_SIGNATURE, "").orEmpty()
        return if (verifiedSignature != signature(profile.hotspotSsid, profile.hotspotPassword)) {
            Requirement.PROFILE_UNVERIFIED
        } else {
            null
        }
    }

    fun requiresVerification(context: Context): Boolean {
        return currentRequirement(context) != null
    }

    fun markVerified(
        context: Context,
        hotspotSsid: String,
        hotspotPassword: String
    ) {
        prefs(context)
            .edit()
            .putString(KEY_VERIFIED_SIGNATURE, signature(hotspotSsid, hotspotPassword))
            .apply()
    }

    fun invalidate(context: Context) {
        prefs(context)
            .edit()
            .remove(KEY_VERIFIED_SIGNATURE)
            .apply()
    }

    private fun signature(
        hotspotSsid: String,
        hotspotPassword: String
    ): String = hotspotSsid.trim() + "|" + hotspotPassword.trim()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
