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
import com.ghalbitnet.meshx2.verified.screen.ProfessionalCardActivity
import com.ghalbitnet.meshx2.verified.share.VerifiedCardPngShareManager
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
            Log.d("GHALBIT-PROFILE", "edit_open")
            editLauncher.launch(Intent(this, EditMyProfileActivity::class.java))
        }
        findViewById<Button>(R.id.btnPreviewPublicCard).setOnClickListener {
            openPublicCardPreview()
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
                txtProfileMeta.text = buildVisibleProfileSummary(profile)
                Log.d("GHALBIT-PROFILE", "rendered")
                Log.d("GHALBIT-NAMECARD", "rendered self=${profile.globalId}")
            }
        }
    }

    private fun buildVisibleProfileSummary(profile: CommunityProfile): String {
        val skillText = profile.skillTags.joinToString(", ").ifBlank { "Belum diisi" }
        val privacyText = if (profile.isPublicProfile) "Profil publik aktif" else "Profil masih lokal/pribadi"
        val relayText = if (profile.isRelayDiscoveryEnabled) "Bisa ditemukan lewat relay" else "Hanya lokal/mesh"
        return """
            Nama tampilan: ${profile.displayName.ifBlank { "Belum diisi" }}
            Nama panggilan: ${profile.nickname.ifBlank { "Belum diisi" }}
            Peran / jabatan: ${profile.roleTitle.ifBlank { "Belum diisi" }}
            Komunitas: ${profile.communityName.ifBlank { "Belum diisi" }}
            Organisasi: ${profile.organization ?: "Belum diisi"}
            Wilayah: ${profile.region.ifBlank { "Belum diisi" }}

            Tentang / visi / misi:
            ${profile.bio.ifBlank { "Belum diisi" }}

            Keahlian / proyek / bantuan:
            $skillText

            Status: ${profile.statusMessage.ifBlank { profile.statusType.wireValue }}
            Privasi: $privacyText • $relayText
            Global ID: ${profile.globalId}
            Versi profil: ${profile.profileVersion}
        """.trimIndent()
    }

    private fun openPublicCardPreview() {
        val profile = currentProfile ?: return
        Log.d("GHALBIT-NAMECARD", "professional_preview_open")
        startActivity(
            ProfessionalCardActivity.createIntent(
                context = this,
                globalId = profile.globalId,
                displayName = profile.primaryName,
                role = profile.roleTitle.ifBlank { "Community Member" },
                community = profile.communityName.ifBlank { "GHALBITNET" },
                trustScore = 0,
                verified = profile.publicKeyHash.isNotBlank(),
                profilePhotoUri = profile.avatarUri,
                nickname = profile.nickname
            )
        )
    }

    private fun shareCurrentQr() {
        val profile = currentProfile ?: return
        val model = VerifiedCardPngShareManager.modelFromProfile(profile, verified = profile.publicKeyHash.isNotBlank())
        val shareIntent = VerifiedCardPngShareManager.createSharePngIntent(this, model)
        startActivity(Intent.createChooser(shareIntent, "Bagikan Kartu GHALBIT"))
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
