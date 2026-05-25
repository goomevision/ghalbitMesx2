package com.ghalbitnet.meshx2.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.access.HotspotBlocklistAssistant
import com.ghalbitnet.meshx2.access.HotspotNetworkScanner
import com.ghalbitnet.meshx2.access.HotspotPasswordRotationAdvisor
import com.ghalbitnet.meshx2.access.ProviderClientActionManager
import com.ghalbitnet.meshx2.access.ProviderNetworkStatus
import com.ghalbitnet.meshx2.access.ProviderNetworkStatusEvaluator
import com.ghalbitnet.meshx2.access.ClientTrustLevel
import com.ghalbitnet.meshx2.access.NetworkAccessPolicy
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.core.network.InternetProviderAccessManager
import com.ghalbitnet.meshx2.core.network.InternetProviderConventionManager
import com.ghalbitnet.meshx2.core.network.InternetProviderReadinessManager
import com.ghalbitnet.meshx2.security.KeyStoreManager
import com.ghalbitnet.meshx2.vpn.VpnLogManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class InternetSharingSettingsActivity : AppCompatActivity() {

    private lateinit var txtStatus: TextView
    private lateinit var txtSummary: TextView
    private lateinit var txtHotspotClientSummary: TextView
    private lateinit var txtProviderNetworkStatus: TextView
    private lateinit var txtPasswordRotationSuggestion: TextView
    private lateinit var switchConsent: SwitchCompat
    private lateinit var edtSsid: EditText
    private lateinit var edtPassword: EditText
    private lateinit var btnSave: Button
    private lateinit var btnOpenHotspotSettings: Button
    private lateinit var btnOpenHotspotBlocklistAssistant: Button
    private lateinit var btnOpenCommunitySessions: Button
    private lateinit var btnGenerateHotspotPassword: Button
    private lateinit var btnManualHotspotScan: Button
    private lateinit var globalId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_internet_sharing_settings)

        globalId = GlobalMeshIdentityManager.buildGlobalId(KeyStoreManager(this).publicKeyBase64)

        txtStatus = findViewById(R.id.txtSharingStatus)
        txtSummary = findViewById(R.id.txtSharingSummary)
        txtHotspotClientSummary = findViewById(R.id.txtHotspotClientSummary)
        txtProviderNetworkStatus = findViewById(R.id.txtProviderNetworkStatus)
        txtPasswordRotationSuggestion = findViewById(R.id.txtPasswordRotationSuggestion)
        switchConsent = findViewById(R.id.switchSharingConsent)
        edtSsid = findViewById(R.id.edtSharingSsid)
        edtPassword = findViewById(R.id.edtSharingPassword)
        btnSave = findViewById(R.id.btnSaveSharingProfile)
        btnOpenHotspotSettings = findViewById(R.id.btnOpenHotspotSettings)
        btnOpenHotspotBlocklistAssistant = findViewById(R.id.btnOpenHotspotBlocklistAssistant)
        btnOpenCommunitySessions = findViewById(R.id.btnOpenCommunitySessions)
        btnGenerateHotspotPassword = findViewById(R.id.btnGenerateHotspotPassword)
        btnManualHotspotScan = findViewById(R.id.btnManualHotspotScan)

        bindActions()
        bindState()
    }

    override fun onResume() {
        super.onResume()
        bindState()
    }

    private fun bindActions() {
        btnSave.setOnClickListener {
            runCatching {
                val recommendedSsid = InternetProviderConventionManager.recommendedSsid(globalId)
                val recommendedPassword = InternetProviderConventionManager.STANDARD_PASSWORD
                val enteredSsid =
                    edtSsid.text?.toString().orEmpty().ifBlank {
                        recommendedSsid
                    }
                val enteredPassword =
                    edtPassword.text?.toString().orEmpty().ifBlank {
                        recommendedPassword
                    }
                val aligned =
                    InternetProviderConventionManager.isAligned(
                        enteredSsid,
                        enteredPassword,
                        globalId
                    )
                val desiredSsid = if (aligned) enteredSsid else recommendedSsid
                val desiredPassword = if (aligned) enteredPassword else recommendedPassword

                if (!aligned) {
                    edtSsid.setText(recommendedSsid)
                    edtPassword.setText(recommendedPassword)
                }

                InternetProviderAccessManager.save(
                    context = this,
                    consentGiven = switchConsent.isChecked,
                    hotspotSsid = desiredSsid,
                    hotspotPassword = desiredPassword
                )
                bindState()
                val toastMessage =
                    if (aligned) {
                        getString(R.string.provider_settings_saved_local)
                    } else {
                        getString(
                            R.string.provider_settings_corrected_to_standard,
                            recommendedSsid,
                            recommendedPassword
                        )
                    }
                Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(
                    this,
                    getString(R.string.provider_settings_save_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnOpenHotspotSettings.setOnClickListener {
            HotspotVerificationManager.invalidate(this)
            HotspotSettingsNavigator.openHotspotSettings(this)
        }

        btnOpenHotspotBlocklistAssistant.setOnClickListener {
            startActivity(Intent(this, UnauthorizedClientsActivity::class.java))
        }

        btnOpenCommunitySessions.setOnClickListener {
            startActivity(Intent(this, com.ghalbitnet.meshx2.access.CommunitySessionActivity::class.java))
        }

        btnGenerateHotspotPassword.setOnClickListener {
            val generated = HotspotPasswordRotationAdvisor.generatePassword()
            txtPasswordRotationSuggestion.text =
                getString(R.string.provider_network_password_value, generated)
            ProviderClientActionManager.recommendPasswordRotation(this, "Provider menghasilkan password hotspot baru.")
            HotspotSettingsNavigator.openForPasswordChange(this)
        }

        btnManualHotspotScan.setOnClickListener {
            VpnLogManager.info("MANUAL_HOTSPOT_SCAN_REQUESTED", "source=InternetSharingSettingsActivity")
            lifecycleScope.launch {
                HotspotNetworkScanner.scan(this@InternetSharingSettingsActivity)
                bindState()
                Toast.makeText(
                    this@InternetSharingSettingsActivity,
                    "Scan hotspot selesai. Ringkasan diperbarui.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun bindState() {
        val profile = InternetProviderAccessManager.snapshot(this)
        val readiness = InternetProviderReadinessManager.snapshot(this)
        val recommendedSsid = InternetProviderConventionManager.recommendedSsid(globalId)
        val recommendedPassword = InternetProviderConventionManager.STANDARD_PASSWORD

        switchConsent.isChecked = profile.consentGiven
        if (!edtSsid.isFocused) {
            edtSsid.setText(profile.hotspotSsid.ifBlank { recommendedSsid })
        }
        if (!edtPassword.isFocused) {
            edtPassword.setText(profile.hotspotPassword.ifBlank { recommendedPassword })
        }

        txtStatus.text = readiness.label(this)
        txtSummary.text =
            getString(
                R.string.provider_settings_summary,
                if (readiness.conventionAligned) {
                    getString(
                        R.string.provider_settings_summary_ready_local,
                        readiness.detail(this)
                    )
                } else {
                    getString(
                        R.string.provider_settings_summary_mismatch_system,
                        readiness.detail(this),
                        recommendedSsid,
                        recommendedPassword
                    )
                },
                if (profile.credentialsReady) profile.hotspotSsid.ifBlank { "-" } else "-",
                if (profile.credentialsReady) profile.maskedPassword() else "-"
            )
        val clientSummary = HotspotBlocklistAssistant.summary(this)
        val unauthorizedMainCount =
            HotspotBlocklistAssistant.snapshot(this).count {
                it.authStatus == NetworkAccessPolicy.AuthStatus.UNKNOWN_NO_HELLO_AUTH ||
                    it.authStatus == NetworkAccessPolicy.AuthStatus.UNAUTHORIZED ||
                    it.authStatus == NetworkAccessPolicy.AuthStatus.EXPIRED ||
                    it.trustLevel == ClientTrustLevel.SUSPICIOUS
            }
        val providerStatus = ProviderNetworkStatusEvaluator.evaluate(this)
        txtHotspotClientSummary.text =
            getString(
                R.string.hotspot_blocklist_summary_value,
                unauthorizedMainCount,
                providerStatus.blockedCount,
                clientSummary.manualAllowedCount,
                providerStatus.suspiciousCount,
                clientSummary.latestDetectedAt?.let {
                    SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()).format(Date(it))
                } ?: "-"
            )
        txtProviderNetworkStatus.text =
            getString(
                R.string.provider_network_status_value,
                providerStatus.title,
                if (unauthorizedMainCount > 0) {
                    getString(R.string.provider_network_unauthorized_warning)
                } else if (providerStatus.blockedCount > 0) {
                    getString(R.string.provider_network_blocked_warning)
                } else {
                    providerStatus.detail
                }
            )
        txtProviderNetworkStatus.setTextColor(
            when (providerStatus.status) {
                ProviderNetworkStatus.SAFE -> 0xFF9FE870.toInt()
                ProviderNetworkStatus.WARNING -> 0xFFFFD54F.toInt()
                ProviderNetworkStatus.RISK -> 0xFFFFB74D.toInt()
                ProviderNetworkStatus.DANGER -> 0xFFFF6E6E.toInt()
            }
        )
        val recommendation = HotspotPasswordRotationAdvisor.evaluate(this)
        txtPasswordRotationSuggestion.text =
            getString(
                R.string.provider_network_password_value,
                if (recommendation.shouldRecommend) {
                    "${recommendation.reason}\nPassword baru: ${recommendation.suggestedPassword}"
                } else {
                    recommendation.reason
                }
            )
    }
}
