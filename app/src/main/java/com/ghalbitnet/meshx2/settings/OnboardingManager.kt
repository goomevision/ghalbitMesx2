package com.ghalbitnet.meshx2.settings

import android.content.Context
import com.ghalbitnet.meshx2.core.network.InternetProviderAccessManager
import com.ghalbitnet.meshx2.settings.HotspotQrCapabilityManager
import com.ghalbitnet.meshx2.settings.WifiHotspotQrProofManager

object OnboardingManager {

    private const val PREFS_NAME = "mesh_onboarding"
    private const val KEY_COMPLETED = "completed"
    private const val KEY_VERSION = "version"
    private const val KEY_RELAY_ENABLED = "relay_enabled"
    private const val KEY_PROVIDER_ENABLED = "provider_enabled"
    private const val KEY_CONTRIBUTION_APPROVED = "contribution_approved"
    private const val CURRENT_VERSION = 2

    data class Snapshot(
        val completed: Boolean,
        val relayEnabled: Boolean,
        val providerEnabled: Boolean,
        val contributionApproved: Boolean,
        val participantRoles: List<String>
    )

    fun snapshot(context: Context): Snapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val relayEnabled = prefs.getBoolean(KEY_RELAY_ENABLED, false)
        val providerEnabled = prefs.getBoolean(KEY_PROVIDER_ENABLED, false)
        val providerProfile = InternetProviderAccessManager.snapshot(context)
        val agreementApproved = prefs.getBoolean(KEY_CONTRIBUTION_APPROVED, false)
        val versionReady = prefs.getInt(KEY_VERSION, 0) >= CURRENT_VERSION
        val credentialsReady = providerProfile.credentialsReady
        val qrProofReady =
            !HotspotQrCapabilityManager.isQrProofRequired() ||
                WifiHotspotQrProofManager.hasValidProof(
                    context,
                    providerProfile.hotspotSsid,
                    providerProfile.hotspotPassword
                )
        val participantRoles =
            buildList {
                add("USER")
                if (relayEnabled) add("RELAY")
                if (providerEnabled) add("PROVIDER")
            }
        return Snapshot(
            completed =
                prefs.getBoolean(KEY_COMPLETED, false) &&
                    versionReady &&
                    agreementApproved &&
                    credentialsReady &&
                    qrProofReady,
            relayEnabled = relayEnabled,
            providerEnabled = providerEnabled,
            contributionApproved = agreementApproved,
            participantRoles = participantRoles
        )
    }

    fun isCompleted(context: Context): Boolean = snapshot(context).completed

    fun save(
        context: Context,
        relayEnabled: Boolean,
        providerEnabled: Boolean,
        contributionApproved: Boolean
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_COMPLETED, true)
            .putInt(KEY_VERSION, CURRENT_VERSION)
            .putBoolean(KEY_RELAY_ENABLED, relayEnabled)
            .putBoolean(KEY_PROVIDER_ENABLED, providerEnabled)
            .putBoolean(KEY_CONTRIBUTION_APPROVED, contributionApproved)
            .apply()
    }
}
