package com.ghalbitnet.meshx2.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.call.CallSessionActivity
import com.ghalbitnet.meshx2.chat.ChatActivity
import com.ghalbitnet.meshx2.core.utils.UiFeedbackManager
import com.ghalbitnet.meshx2.routing.CallRouteDiscoveryManager
import com.ghalbitnet.meshx2.ui.CallSearchingToneManager
import com.ghalbitnet.meshx2.ui.GhalbitTheme
import com.ghalbitnet.meshx2.ui.RouteSearchingAnimator
import com.ghalbitnet.meshx2.verified.screen.ProfessionalCardActivity
import com.ghalbitnet.meshx2.verified.share.VerifiedCardPngShareManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactNameCardActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA_GLOBAL_ID = "extra.globalId"
        private const val EXTRA_CHAT_ID = "extra.chatId"
        private const val EXTRA_FALLBACK_NAME = "extra.fallbackName"
        private const val EXTRA_PUBLIC_KEY_HASH = "extra.publicKeyHash"
        private const val EXTRA_ROUTE_HINT = "extra.routeHint"
        private const val EXTRA_SCANNED_PAYLOAD = "extra.scannedPayload"

        fun createIntent(
            context: Context,
            globalId: String?,
            chatId: String?,
            fallbackName: String,
            publicKeyHash: String? = null,
            routeHint: String? = null
        ): Intent {
            return Intent(context, ContactNameCardActivity::class.java).apply {
                putExtra(EXTRA_GLOBAL_ID, globalId)
                putExtra(EXTRA_CHAT_ID, chatId)
                putExtra(EXTRA_FALLBACK_NAME, fallbackName)
                putExtra(EXTRA_PUBLIC_KEY_HASH, publicKeyHash)
                putExtra(EXTRA_ROUTE_HINT, routeHint)
            }
        }

        fun createScannedIntent(context: Context, payload: String): Intent {
            return Intent(context, ContactNameCardActivity::class.java).apply {
                putExtra(EXTRA_SCANNED_PAYLOAD, payload)
            }
        }
    }

    private lateinit var txtProfileMeta: TextView
    private var currentProfile: CommunityProfile? = null
    private var currentChatId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GhalbitTheme.applyWindow(this, "contact-card")
        setContentView(R.layout.activity_contact_name_card)
        txtProfileMeta = findViewById(R.id.txtContactProfileMeta)
        title = "Kartu Kontak"

        findViewById<Button>(R.id.btnCardChat).setOnClickListener { openChat() }
        findViewById<Button>(R.id.btnCardCall).setOnClickListener { openCall() }
        findViewById<Button>(R.id.btnCardSave).setOnClickListener { showSaveAliasDialog() }
        findViewById<Button>(R.id.btnCardVerify).setOnClickListener { verifyProfile() }
        findViewById<Button>(R.id.btnCardShare).setOnClickListener { shareCardPng() }
        findViewById<Button>(R.id.btnCardProfessional).setOnClickListener { openProfessionalCard() }

        render()
    }

    private fun render() {
        lifecycleScope.launch(Dispatchers.IO) {
            val scannedPayload = intent.getStringExtra(EXTRA_SCANNED_PAYLOAD)
            val profile =
                if (!scannedPayload.isNullOrBlank()) {
                    val payload = ProfileQrCodec.decode(scannedPayload)
                    if (payload != null) {
                        ProfileSyncManager.applyScannedQr(this@ContactNameCardActivity, payload)
                    } else {
                        withContext(Dispatchers.Main) {
                            UiFeedbackManager.showToast(this@ContactNameCardActivity, "Kartu tidak dapat diverifikasi atau data QR rusak.")
                        }
                        null
                    }
                } else {
                    val globalId = intent.getStringExtra(EXTRA_GLOBAL_ID)
                    currentChatId = intent.getStringExtra(EXTRA_CHAT_ID)
                    val fallbackName = intent.getStringExtra(EXTRA_FALLBACK_NAME) ?: "Belum dikenal"
                    val routeHint = intent.getStringExtra(EXTRA_ROUTE_HINT)
                    val profile = ProfileRepository.getResolvedContact(
                        context = this@ContactNameCardActivity,
                        globalId = globalId,
                        chatId = currentChatId,
                        fallbackDisplayName = fallbackName,
                        publicKeyHash = intent.getStringExtra(EXTRA_PUBLIC_KEY_HASH),
                        routeHint = routeHint
                    )
                    if (!globalId.isNullOrBlank()) {
                        ProfileSyncManager.fetchProfile(this@ContactNameCardActivity, globalId)
                    }
                    profile
                }
            if (profile == null) return@launch
            currentProfile = profile
            val qrPayload = ProfileSyncManager.buildSignedQrPayload(this@ContactNameCardActivity, profile, profile.routeHint)
            val qr = ProfileQrCodec.renderBitmap(ProfileQrCodec.encode(qrPayload), 220)
            withContext(Dispatchers.Main) {
                ContactCardRenderer.bind(
                    root = findViewById(R.id.viewContactCard),
                    profile = profile,
                    routeBadge = routeBadge(profile),
                    qrBitmap = qr
                )
                txtProfileMeta.text =
                    "Nama publik: ${profile.displayName}\nAlias lokal: ${profile.localAlias ?: "-"}\nSinkron terakhir: ${if (profile.lastProfileSyncAt > 0) profile.lastProfileSyncAt else "-"}"
            }
        }
    }

    private fun openChat() {
        val profile = currentProfile ?: return
        Log.d("GHALBIT-CARD", "opened from chat")
        startActivity(
            Intent(this, ChatActivity::class.java).apply {
                putExtra("peerName", currentChatId ?: profile.globalId)
                putExtra("peerIp", profile.routeHint ?: "")
                putExtra("peerGlobalId", profile.globalId)
                putExtra("peerDisplayName", profile.primaryName)
            }
        )
    }

    private fun openCall() {
        val profile = currentProfile ?: return
        Log.d("GHALBIT-CARD", "opened from call")
        val callButton = findViewById<Button>(R.id.btnCardCall)
        val previousMeta = txtProfileMeta.text.toString()
        val toneManager = CallSearchingToneManager()
        val animator = RouteSearchingAnimator(lifecycleScope) { text -> txtProfileMeta.text = text }
        callButton.isEnabled = false
        animator.start("Mencari jalur ke kontak")
        toneManager.start()
        lifecycleScope.launch {
            try {
                val discovery =
                    CallRouteDiscoveryManager.discoverForCall(
                        context = this@ContactNameCardActivity,
                        peerName = currentChatId ?: profile.globalId,
                        ipHint = profile.routeHint,
                        globalIdHint = profile.globalId,
                        displayNameHint = profile.primaryName
                    ) { _, label ->
                        animator.update(label)
                    }
                val endpoint = discovery.endpoint
                val targetIp = endpoint?.routeHint ?: endpoint?.transportIp
                if (endpoint == null || targetIp.isNullOrBlank()) {
                    animator.stop("Belum menemukan jalur")
                    UiFeedbackManager.showToast(this@ContactNameCardActivity, "Belum menemukan jalur. Pencarian tetap berjalan.")
                    return@launch
                }
                animator.stop("Jalur ditemukan")
                startActivity(
                    CallSessionActivity.createIntent(
                        context = this@ContactNameCardActivity,
                        peerName = currentChatId ?: profile.globalId,
                        peerIp = targetIp,
                        callId = "call-${System.currentTimeMillis()}",
                        incoming = false,
                        peerGlobalId = endpoint.globalId,
                        peerPublicKey = endpoint.publicKey,
                        peerWalletAddress = endpoint.walletAddress,
                        peerDisplayName = endpoint.displayName ?: profile.primaryName
                    )
                )
            } finally {
                toneManager.stopAndRelease()
                callButton.isEnabled = true
                if (!isFinishing && !isDestroyed) {
                    txtProfileMeta.text = previousMeta
                }
            }
        }
    }

    private fun showSaveAliasDialog() {
        val profile = currentProfile ?: return
        val container =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 24, 32, 12)
            }
        val aliasInput = EditText(this).apply { hint = "Nama di perangkat saya"; setText(profile.localAlias ?: "") }
        val noteInput = EditText(this).apply { hint = "Catatan pribadi"; setText(profile.localNote ?: "") }
        val communityInput = EditText(this).apply { hint = "Label komunitas"; setText(profile.communityLabel ?: "") }
        val favorite = CheckBox(this).apply { text = "Favorit"; isChecked = profile.isFavorite }
        val pinned = CheckBox(this).apply { text = "Pin kontak"; isChecked = profile.isPinned }
        container.addView(aliasInput)
        container.addView(noteInput)
        container.addView(communityInput)
        container.addView(favorite)
        container.addView(pinned)
        AlertDialog.Builder(this)
            .setTitle("Simpan sebagai kontak lokal")
            .setView(container)
            .setPositiveButton("Simpan") { _, _ ->
                ProfileRepository.saveLocalAlias(
                    context = this,
                    globalId = profile.globalId,
                    chatId = currentChatId ?: profile.globalId,
                    publicDisplayName = profile.displayName,
                    publicNickname = profile.nickname,
                    localAlias = aliasInput.text.toString(),
                    localNote = noteInput.text.toString(),
                    communityLabel = communityInput.text.toString(),
                    savedAsName = aliasInput.text.toString(),
                    localTags = profile.localTags,
                    favorite = favorite.isChecked,
                    pinned = pinned.isChecked
                )
                render()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun verifyProfile() {
        val profile = currentProfile ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val verified = ProfileSyncManager.verifyProfile(this@ContactNameCardActivity, profile.globalId)
            withContext(Dispatchers.Main) {
                Log.d("GHALBIT-CARD-QR", "verified")
                AlertDialog.Builder(this@ContactNameCardActivity)
                    .setMessage(if (verified) "Profil terverifikasi relay." else "Profil belum bisa diverifikasi.")
                    .setPositiveButton("OK", null)
                    .show()
                if (verified) render()
            }
        }
    }

    private fun shareCardPng() {
        val profile = currentProfile ?: return
        val model = VerifiedCardPngShareManager.modelFromProfile(profile)
        startActivity(Intent.createChooser(VerifiedCardPngShareManager.createSharePngIntent(this, model), "Bagikan Kartu PNG"))
    }

    private fun routeBadge(profile: CommunityProfile): String {
        return when {
            !profile.routeHint.isNullOrBlank() && profile.routeHint!!.contains("http", true) -> "RELAY"
            !profile.routeHint.isNullOrBlank() -> "MESH"
            else -> "OFFLINE"
        }
    }

    private fun openProfessionalCard() {
        val profile = currentProfile ?: return
        startActivity(
            ProfessionalCardActivity.createIntent(
                context = this,
                globalId = profile.globalId,
                displayName = profile.primaryName,
                role = profile.roleTitle.ifBlank { "Community Member" },
                community = profile.communityName.ifBlank { "GHALBITNET" },
                trustScore = 0,
                verified = ProfessionalCardDataMapper.verifyProfile(profile) == ProfileVerificationStatus.VALID_SIGNATURE,
                profilePhotoUri = profile.avatarUri,
                nickname = profile.nickname
            )
        )
    }
}
