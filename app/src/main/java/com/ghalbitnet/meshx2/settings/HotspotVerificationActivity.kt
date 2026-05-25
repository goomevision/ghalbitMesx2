package com.ghalbitnet.meshx2.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.ghalbitnet.meshx2.MainActivity
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.core.network.InternetProviderAccessManager
import com.ghalbitnet.meshx2.core.network.InternetProviderConventionManager
import com.ghalbitnet.meshx2.core.network.InternetProviderReadinessManager
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.core.network.HotspotSystemConfigManager
import com.ghalbitnet.meshx2.security.KeyStoreManager

class HotspotVerificationActivity : AppCompatActivity() {

    private lateinit var txtSummary: TextView
    private lateinit var txtHint: TextView
    private lateinit var txtQrStatus: TextView
    private lateinit var edtSsid: EditText
    private lateinit var edtPassword: EditText
    private lateinit var btnUploadQr: Button
    private lateinit var btnVerify: Button
    private lateinit var btnOpenHotspotSettings: Button
    private lateinit var globalId: String
    private val qrImagePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val data = WifiHotspotQrProofManager.decodeFromImage(this, uri)
            if (data == null) {
                Toast.makeText(this, getString(R.string.hotspot_qr_decode_failed), Toast.LENGTH_LONG).show()
                updateQrStatus()
                return@registerForActivityResult
            }
            edtSsid.setText(data.ssid)
            edtPassword.setText(data.password)
            Toast.makeText(this, getString(R.string.hotspot_qr_decode_success, data.ssid), Toast.LENGTH_SHORT).show()
            updateQrStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hotspot_verification)

        globalId = GlobalMeshIdentityManager.buildGlobalId(KeyStoreManager(this).publicKeyBase64)

        txtSummary = findViewById(R.id.txtHotspotVerificationSummary)
        txtHint = findViewById(R.id.txtHotspotVerificationHint)
        txtQrStatus = findViewById(R.id.txtHotspotVerificationQrStatus)
        edtSsid = findViewById(R.id.edtHotspotVerificationSsid)
        edtPassword = findViewById(R.id.edtHotspotVerificationPassword)
        btnUploadQr = findViewById(R.id.btnHotspotVerificationUploadQr)
        btnVerify = findViewById(R.id.btnHotspotVerificationContinue)
        btnOpenHotspotSettings = findViewById(R.id.btnHotspotVerificationSettings)

        bindState()
        bindActions()
    }

    override fun onResume() {
        super.onResume()
        bindState()
    }

    private fun bindState() {
        val profile = InternetProviderAccessManager.snapshot(this)
        val readiness = InternetProviderReadinessManager.snapshot(this)
        val recommendedSsid = InternetProviderConventionManager.recommendedSsid(globalId)
        val requirement = HotspotVerificationManager.currentRequirement(this)

        txtSummary.text =
            getString(
                R.string.hotspot_verification_summary,
                readiness.label(this),
                profile.hotspotSsid.ifBlank { recommendedSsid }
            )
        txtHint.text =
            when (requirement) {
                HotspotVerificationManager.Requirement.HOTSPOT_OFF ->
                    getString(R.string.hotspot_verification_hotspot_off)
                HotspotVerificationManager.Requirement.PROFILE_MISSING ->
                    getString(R.string.hotspot_verification_profile_missing)
                HotspotVerificationManager.Requirement.SYSTEM_MISMATCH ->
                    getString(R.string.hotspot_verification_system_mismatch)
                HotspotVerificationManager.Requirement.QR_PROOF_REQUIRED ->
                    getString(R.string.hotspot_qr_required)
                HotspotVerificationManager.Requirement.PROFILE_UNVERIFIED ->
                    getString(R.string.hotspot_verification_profile_unverified)
                null -> getString(R.string.hotspot_verification_profile_ready)
            }
        btnVerify.isEnabled = readiness.hotspotActive
        if (!edtSsid.isFocused) {
            edtSsid.setText(profile.hotspotSsid.ifBlank { recommendedSsid })
        }
        if (!edtPassword.isFocused) {
            edtPassword.setText(profile.hotspotPassword)
        }
        updateQrStatus()
    }

    private fun bindActions() {
        btnUploadQr.setOnClickListener {
            qrImagePicker.launch("image/*")
        }

        btnVerify.setOnClickListener {
            val readiness = InternetProviderReadinessManager.snapshot(this)
            if (!readiness.hotspotActive) {
                Toast.makeText(this, getString(R.string.hotspot_verification_hotspot_off), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val enteredSsid = edtSsid.text?.toString().orEmpty().trim()
            val enteredPassword = edtPassword.text?.toString().orEmpty().trim()

            if (enteredSsid.isBlank()) {
                Toast.makeText(this, getString(R.string.onboarding_ssid_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (enteredPassword.length < 8) {
                Toast.makeText(this, getString(R.string.onboarding_password_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val systemConfig = HotspotSystemConfigManager.snapshot(this)
            if (
                systemConfig.readable &&
                    (
                        systemConfig.ssid?.trim() != enteredSsid ||
                            systemConfig.password?.trim() != enteredPassword
                        )
            ) {
                Toast.makeText(this, getString(R.string.hotspot_verification_system_mismatch), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (
                HotspotQrCapabilityManager.isQrProofRequired() &&
                    !WifiHotspotQrProofManager.hasValidProof(this, enteredSsid, enteredPassword)
            ) {
                Toast.makeText(this, getString(R.string.hotspot_qr_required), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val currentProfile = InternetProviderAccessManager.snapshot(this)
            val changed =
                enteredSsid != currentProfile.hotspotSsid || enteredPassword != currentProfile.hotspotPassword

            InternetProviderAccessManager.save(
                context = this,
                consentGiven = currentProfile.consentGiven,
                hotspotSsid = enteredSsid,
                hotspotPassword = enteredPassword
            )
            if (HotspotQrCapabilityManager.isQrProofRequired()) {
                WifiHotspotQrProofManager.markProofFromCredentials(
                    this,
                    enteredSsid,
                    enteredPassword
                )
            }
            HotspotVerificationManager.markVerified(this, enteredSsid, enteredPassword)

            val toastMessage =
                if (changed) {
                    getString(R.string.hotspot_verification_updated)
                } else {
                    getString(R.string.hotspot_verification_success)
                }
            Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        btnOpenHotspotSettings.setOnClickListener {
            HotspotVerificationManager.invalidate(this)
            runCatching {
                startActivity(Intent("android.settings.TETHER_SETTINGS"))
            }.recoverCatching {
                startActivity(Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS))
            }
        }
    }

    override fun onBackPressed() {
        finishAffinity()
    }

    private fun updateQrStatus() {
        val required = HotspotQrCapabilityManager.isQrProofRequired()
        val ssid = edtSsid.text?.toString().orEmpty().trim()
        val password = edtPassword.text?.toString().orEmpty().trim()
        txtQrStatus.text =
            when {
                required && WifiHotspotQrProofManager.hasValidProof(this, ssid, password) ->
                    getString(R.string.hotspot_qr_status_ready)
                required ->
                    getString(R.string.hotspot_qr_status_required, HotspotQrCapabilityManager.deviceLabel())
                else ->
                    getString(R.string.hotspot_qr_status_optional, HotspotQrCapabilityManager.deviceLabel())
            }
    }
}
