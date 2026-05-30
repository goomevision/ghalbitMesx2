package com.ghalbitnet.meshx2.profile

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.ui.GhalbitTheme
import com.ghalbitnet.meshx2.ui.RuntimeSoftBannerManager
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyProfileActivity : AppCompatActivity() {
    private lateinit var cardView: MyNameCardView
    private lateinit var txtProfileMeta: TextView
    private lateinit var runtimeSoftBanner: RuntimeSoftBannerManager
    private var currentProfile: CommunityProfile? = null

    private val editLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            renderProfile()
        }

    private val scanLauncher =
        registerForActivityResult(ScanContract()) { result ->
            handleScanResult(result.contents)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GhalbitTheme.applyWindow(this, "my-profile")
        setContentView(R.layout.activity_my_profile)
        runtimeSoftBanner = RuntimeSoftBannerManager.attach(this)
        cardView = findViewById(R.id.viewMyNameCard)
        txtProfileMeta = findViewById(R.id.txtProfileMeta)
        title = "Kartu Nama Saya"
        Log.d("GHALBIT-PROFILE", "self opened")

        findViewById<Button>(R.id.btnEditMyProfile).setOnClickListener {
            editLauncher.launch(Intent(this, EditMyProfileActivity::class.java))
        }
        findViewById<Button>(R.id.btnShareMyQr).setOnClickListener {
            shareCurrentQr()
        }
        findViewById<Button>(R.id.btnScanContactQr).setOnClickListener {
            scanLauncher.launch(
                ScanOptions().apply {
                    setPrompt("Pindai kartu nama GhalbitNet")
                    setBeepEnabled(false)
                    setOrientationLocked(false)
                }
            )
        }
        findViewById<Button>(R.id.btnSyncMyProfile).setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val ok = ProfileSyncManager.uploadMyProfile(this@MyProfileActivity)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        runtimeSoftBanner.showMessage(
                            key = "profile:sync:ok",
                            title = "Profil komunitas tersinkron",
                            detail = "Kartu nama publik berhasil diperbarui",
                            priority = 4
                        )
                    }
                }
            }
        }

        renderProfile()
    }

    override fun onResume() {
        super.onResume()
        runtimeSoftBanner.onHostResume()
        renderProfile()
    }

    override fun onPause() {
        runtimeSoftBanner.onHostPause()
        super.onPause()
    }

    override fun onDestroy() {
        runtimeSoftBanner.onHostDestroy()
        super.onDestroy()
    }

    private fun renderProfile() {
        lifecycleScope.launch(Dispatchers.IO) {
            val profile = ProfileRepository.getOrCreateMyProfile(this@MyProfileActivity)
            currentProfile = profile
            val payload = ProfileQrCodec.encode(ProfileSyncManager.buildSignedQrPayload(this@MyProfileActivity, profile, relayHint = null))
            val bitmap = ProfileQrCodec.renderBitmap(payload, 260)
            withContext(Dispatchers.Main) {
                cardView.render(profile, routeBadge = if (profile.isRelayDiscoveryEnabled) "RELAY" else "MESH", qrBitmap = bitmap)
                txtProfileMeta.text =
                    "Global ID: ${profile.globalId}\nVersi profil: ${profile.profileVersion}\nSync relay: ${if (profile.isPublicProfile) "aktif" else "mati"}"
            }
        }
    }

    private fun shareCurrentQr() {
        val profile = currentProfile ?: return
        val payload = ProfileQrCodec.encode(ProfileSyncManager.buildSignedQrPayload(this, profile, relayHint = null))
        startActivity(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Kartu Nama GhalbitNet")
                putExtra(Intent.EXTRA_TEXT, payload)
            }
        )
    }

    private fun handleScanResult(contents: String?) {
        if (contents.isNullOrBlank()) return
        Log.d("GHALBIT-CARD-QR", "scanned")
        startActivity(
            ContactNameCardActivity.createScannedIntent(
                context = this,
                payload = contents
            )
        )
    }
}
