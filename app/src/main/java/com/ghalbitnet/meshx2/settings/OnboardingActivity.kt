package com.ghalbitnet.meshx2.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.ghalbitnet.meshx2.MainActivity
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.core.network.InternetProviderAccessManager
import com.ghalbitnet.meshx2.core.network.InternetProviderConventionManager
import com.ghalbitnet.meshx2.core.network.InternetProviderReadinessManager
import com.ghalbitnet.meshx2.security.KeyStoreManager

class OnboardingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SETTINGS_MODE = "settings_mode"
    }

    private lateinit var edtSsid: EditText
    private lateinit var edtPassword: EditText
    private lateinit var txtQrStatus: TextView
    private lateinit var checkAgreement: CheckBox
    private lateinit var checkRelay: CheckBox
    private lateinit var checkProvider: CheckBox
    private lateinit var btnUploadQr: Button
    private lateinit var btnContinue: Button

    private lateinit var globalId: String
    private var settingsMode: Boolean = false
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
            txtQrStatus.text = getString(R.string.hotspot_qr_decode_success, data.ssid)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        globalId = GlobalMeshIdentityManager.buildGlobalId(KeyStoreManager(this).publicKeyBase64)
        settingsMode = intent.getBooleanExtra(EXTRA_SETTINGS_MODE, false)

        edtSsid = findViewById(R.id.edtOnboardingSsid)
        edtPassword = findViewById(R.id.edtOnboardingPassword)
        txtQrStatus = findViewById(R.id.txtOnboardingQrStatus)
        checkAgreement = findViewById(R.id.checkOnboardingAgreement)
        checkRelay = findViewById(R.id.checkRoleRelay)
        checkProvider = findViewById(R.id.checkRoleProvider)
        btnUploadQr = findViewById(R.id.btnOnboardingUploadQr)
        btnContinue = findViewById(R.id.btnOnboardingContinue)

        val onboarding = OnboardingManager.snapshot(this)
        val providerProfile = InternetProviderAccessManager.snapshot(this)
        edtSsid.setText(providerProfile.hotspotSsid.ifBlank { InternetProviderConventionManager.recommendedSsid(globalId) })
        edtPassword.setText(providerProfile.hotspotPassword.ifBlank { InternetProviderConventionManager.STANDARD_PASSWORD })
        checkAgreement.isChecked = onboarding.contributionApproved
        checkRelay.isChecked = onboarding.relayEnabled
        checkProvider.isChecked = onboarding.providerEnabled
        updateQrStatus()

        btnUploadQr.setOnClickListener {
            qrImagePicker.launch("image/*")
        }

        btnContinue.setOnClickListener {
            if (!checkAgreement.isChecked) {
                Toast.makeText(this, getString(R.string.onboarding_agreement_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val ssid = edtSsid.text?.toString().orEmpty().ifBlank {
                InternetProviderConventionManager.recommendedSsid(globalId)
            }
            val password = edtPassword.text?.toString().orEmpty().ifBlank {
                InternetProviderConventionManager.STANDARD_PASSWORD
            }
            if (ssid.isBlank()) {
                Toast.makeText(this, getString(R.string.onboarding_ssid_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 8) {
                Toast.makeText(this, getString(R.string.onboarding_password_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!InternetProviderReadinessManager.snapshot(this).hotspotActive) {
                Toast.makeText(this, getString(R.string.onboarding_hotspot_required), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (
                HotspotQrCapabilityManager.isQrProofRequired() &&
                    !WifiHotspotQrProofManager.hasValidProof(this, ssid, password)
            ) {
                Toast.makeText(this, getString(R.string.hotspot_qr_required), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            OnboardingManager.save(
                context = this,
                relayEnabled = checkRelay.isChecked,
                providerEnabled = checkProvider.isChecked,
                contributionApproved = true
            )
            InternetProviderAccessManager.save(
                context = this,
                consentGiven = checkRelay.isChecked || checkProvider.isChecked,
                hotspotSsid = ssid,
                hotspotPassword = password
            )
            if (HotspotQrCapabilityManager.isQrProofRequired()) {
                WifiHotspotQrProofManager.markProofFromCredentials(this, ssid, password)
            }

            if (settingsMode) {
                Toast.makeText(this, getString(R.string.onboarding_roles_updated), Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
        }
    }

    private fun updateQrStatus() {
        val profile = InternetProviderAccessManager.snapshot(this)
        val required = HotspotQrCapabilityManager.isQrProofRequired()
        txtQrStatus.text =
            when {
                required &&
                    WifiHotspotQrProofManager.hasValidProof(
                        this,
                        profile.hotspotSsid,
                        profile.hotspotPassword
                    ) -> getString(R.string.hotspot_qr_status_ready)
                required -> getString(
                    R.string.hotspot_qr_status_required,
                    HotspotQrCapabilityManager.deviceLabel()
                )
                else -> getString(
                    R.string.hotspot_qr_status_optional,
                    HotspotQrCapabilityManager.deviceLabel()
                )
            }
    }
}
