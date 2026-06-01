package com.ghalbitnet.meshx2.verified.screen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ghalbitnet.meshx2.BuildConfig
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.profile.CommunityProfile
import com.ghalbitnet.meshx2.profile.CommunityStatusType
import com.ghalbitnet.meshx2.profile.ContactCardTheme
import com.ghalbitnet.meshx2.profile.ProfessionalCardDataMapper
import com.ghalbitnet.meshx2.profile.ProfessionalReferralResolver
import com.ghalbitnet.meshx2.profile.ProfileRepository
import com.ghalbitnet.meshx2.profile.ProfileVerificationStatus
import com.ghalbitnet.meshx2.profile.SafeAvatarLoader
import com.ghalbitnet.meshx2.ui.GhalbitTheme
import com.ghalbitnet.meshx2.verified.share.VerifiedCardPngShareManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfessionalCardActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA_GLOBAL_ID = "verified.globalId"
        private const val EXTRA_DISPLAY_NAME = "verified.displayName"
        private const val EXTRA_ROLE = "verified.role"
        private const val EXTRA_COMMUNITY = "verified.community"
        private const val EXTRA_TRUST_SCORE = "verified.trustScore"
        private const val EXTRA_VERIFIED = "verified.verified"
        private const val EXTRA_PROFILE_PHOTO_URI = "verified.profilePhotoUri"
        private const val EXTRA_NICKNAME = "verified.nickname"

        fun createIntent(
            context: Context,
            globalId: String,
            displayName: String,
            role: String,
            community: String,
            trustScore: Int = 0,
            verified: Boolean = true,
            profilePhotoUri: String? = null,
            nickname: String? = null
        ): Intent {
            return Intent(context, ProfessionalCardActivity::class.java).apply {
                putExtra(EXTRA_GLOBAL_ID, globalId)
                putExtra(EXTRA_DISPLAY_NAME, displayName)
                putExtra(EXTRA_ROLE, role)
                putExtra(EXTRA_COMMUNITY, community)
                putExtra(EXTRA_TRUST_SCORE, trustScore)
                putExtra(EXTRA_VERIFIED, verified)
                putExtra(EXTRA_PROFILE_PHOTO_URI, profilePhotoUri)
                putExtra(EXTRA_NICKNAME, nickname)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GhalbitTheme.applyWindow(this, "verified-card")
        setContentView(R.layout.activity_professional_card)
        title = "Kartu Terverifikasi"
        render()
    }

    private fun render() {
        lifecycleScope.launch(Dispatchers.IO) {
            val globalId = intent.getStringExtra(EXTRA_GLOBAL_ID).orEmpty().ifBlank { "GX-UNKNOWN" }
            val profile = ProfileRepository.getResolvedContact(
                context = this@ProfessionalCardActivity,
                globalId = globalId,
                chatId = globalId,
                fallbackDisplayName = intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty().ifBlank { "Pengguna GHALBITNET" },
                publicKeyHash = null,
                routeHint = null
            ).mergeFallback(intent)

            val mapped = ProfessionalCardDataMapper.fromProfile(this@ProfessionalCardActivity, profile)
            val model = mapped.model
            val referralDebug = ProfessionalReferralResolver.resolve(this@ProfessionalCardActivity, profile)
            withContext(Dispatchers.Main) {
                val rankLabel = model.trustRank
                findViewById<TextView>(R.id.txtProfessionalCardTitle).text = "GHALBIT VERIFIED CARD"
                findViewById<TextView>(R.id.txtProfessionalName).text = model.displayName
                findViewById<TextView>(R.id.txtProfessionalRole).text = model.role
                findViewById<TextView>(R.id.txtProfessionalCommunity).text = model.community
                findViewById<TextView>(R.id.txtProfessionalNicknameOrGlobal).text = model.nickname.ifBlank { model.globalId }
                findViewById<TextView>(R.id.txtProfessionalVerifiedBadge).text = verificationLabel(model.verificationStatus, model.tier.name)
                findViewById<TextView>(R.id.txtProfessionalVerifiedBadge).apply {
                    background?.setTint(com.ghalbitnet.meshx2.profile.ProfessionalCardTierSystem.themeFor(model.tier).badgeBgColor)
                    setTextColor(com.ghalbitnet.meshx2.profile.ProfessionalCardTierSystem.themeFor(model.tier).badgeTextColor)
                }
                findViewById<TextView>(R.id.txtProfessionalTrustBadge).text = "Trust Score: ${model.trustScore} • Rank: $rankLabel"
                findViewById<TextView>(R.id.txtProfessionalReferralBadge).text =
                    when {
                        model.referralRewardedCount > 0 -> "Referral: ${model.referralLabel} (rewarded)"
                        model.referralPendingCount > 0 -> "Referral: ${model.referralLabel} (pending reward)"
                        model.referralLabel.equals("belum tersedia", true) -> "Referral: belum tersedia"
                        else -> "Referral: ${model.referralLabel}"
                    }
                findViewById<TextView>(R.id.txtProfessionalMentorBadge).text = "Mentor: ${model.mentorStatus}"
                findViewById<TextView>(R.id.txtProfessionalReputationBadge).text =
                    if (model.communityReputation <= 0 && model.contributionSummary.contains("belum tersedia", ignoreCase = true)) {
                        "Community Reputation: belum tersedia"
                    } else {
                        "Community Reputation: ${model.communityReputation}"
                    }
                findViewById<TextView>(R.id.txtProfessionalUnifiedSummary).text =
                    "Global ID: ${model.globalId}\nLokasi: ${model.region}\nVersi: ${model.profileVersion}\nKontribusi: ${model.contributionSummary}\nStatus Verifikasi: ${verificationLabel(model.verificationStatus, model.tier.name)}"
                if (BuildConfig.DEBUG) {
                    val debugText =
                        "\n\n[REFERRAL DEBUG]\nseen=${referralDebug.seen} saved=${referralDebug.savedContact} verified=${referralDebug.verified} joined=${referralDebug.joined} pending=${referralDebug.pending} rewarded=${referralDebug.rewarded} total=${referralDebug.total}\nsource=${referralDebug.source}\nfallback=${referralDebug.fallbackUsed}\n" +
                            if (referralDebug.debugEvents.isEmpty()) "events=(empty)" else "events=${referralDebug.debugEvents.joinToString(" | ")}"
                    findViewById<TextView>(R.id.txtProfessionalUnifiedSummary).append(debugText)
                }
                bindProfilePhoto(model.profilePhotoUri, model.displayName)
                findViewById<View>(R.id.professionalCardRoot)?.setBackgroundColor(
                    com.ghalbitnet.meshx2.profile.ProfessionalCardTierSystem.themeFor(model.tier).cardGlowColor
                )
                playCardEntryAnimation()
                playVerifiedPulse()
                playQrGlowLite()

                findViewById<Button>(R.id.btnShareProfessionalCard).setOnClickListener {
                    val shareIntent = VerifiedCardPngShareManager.createSharePngIntent(this@ProfessionalCardActivity, model)
                    startActivity(Intent.createChooser(shareIntent, "Bagikan Kartu"))
                }
            }
        }
    }

    private fun CommunityProfile.mergeFallback(intent: Intent): CommunityProfile {
        val role = intent.getStringExtra(EXTRA_ROLE).orEmpty().ifBlank { "Anggota Komunitas" }
        val community = intent.getStringExtra(EXTRA_COMMUNITY).orEmpty().ifBlank { "GhalbitNet Community" }
        val nickname = intent.getStringExtra(EXTRA_NICKNAME).orEmpty()
        val photo = intent.getStringExtra(EXTRA_PROFILE_PHOTO_URI)
        return copy(
            displayName = displayName.ifBlank { intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty().ifBlank { "Pengguna GHALBITNET" } },
            roleTitle = roleTitle.ifBlank { role },
            communityName = communityName.ifBlank { community },
            nickname = this.nickname.ifBlank { nickname },
            avatarUri = this.avatarUri ?: photo,
            statusType = this.statusType,
            cardTheme = this.cardTheme.ifBlankTheme()
        )
    }

    private fun ContactCardTheme.ifBlankTheme(): ContactCardTheme = this

    private fun verificationLabel(status: ProfileVerificationStatus, tier: String): String {
        val statusText = when (status) {
            ProfileVerificationStatus.VALID_SIGNATURE -> "VERIFIED"
            ProfileVerificationStatus.INVALID_SIGNATURE -> "INVALID SIGNATURE"
            ProfileVerificationStatus.UNSIGNED -> "UNSIGNED"
            ProfileVerificationStatus.UNKNOWN -> "UNKNOWN"
        }
        return "$statusText • $tier"
    }

    private fun bindProfilePhoto(photoUri: String?, displayName: String) {
        val img = findViewById<ImageView>(R.id.imgProfessionalProfilePhoto)
        val initial = findViewById<TextView>(R.id.txtProfessionalInitial)
        val first = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "G"
        initial.text = first
        if (photoUri.isNullOrBlank()) {
            img.setImageDrawable(null)
            initial.visibility = View.VISIBLE
            return
        }
        val loaded = SafeAvatarLoader.loadInto(img, photoUri)
        if (!loaded) img.setImageDrawable(null)
        initial.visibility = if (img.drawable != null) View.GONE else View.VISIBLE
    }

    private fun playCardEntryAnimation() {
        val root = findViewById<View>(R.id.professionalCardRoot) ?: return
        root.scaleX = 0.987f
        root.scaleY = 0.987f
        root.alpha = 0.94f
        root.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(220L).start()
    }

    private fun playVerifiedPulse() {
        val badge = findViewById<TextView>(R.id.txtProfessionalVerifiedBadge) ?: return
        if (!(badge.text?.contains("VERIFIED", true) == true)) return
        android.animation.ObjectAnimator.ofFloat(badge, View.SCALE_X, 1f, 1.03f, 1f).apply {
            duration = 1800L
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.RESTART
            start()
        }
    }

    private fun playQrGlowLite() {
        val summary = findViewById<TextView>(R.id.txtProfessionalUnifiedSummary) ?: return
        android.animation.ObjectAnimator.ofFloat(summary, View.ALPHA, 0.96f, 1f, 0.96f).apply {
            duration = 2400L
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.RESTART
            start()
        }
    }
}
