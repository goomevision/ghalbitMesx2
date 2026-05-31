package com.ghalbitnet.meshx2.verified.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.ui.GhalbitTheme
import com.ghalbitnet.meshx2.verified.trust.CommunityReputationEngine
import com.ghalbitnet.meshx2.verified.trust.IdentityLevel
import com.ghalbitnet.meshx2.verified.trust.ProfessionalCardSummaryFactory
import com.ghalbitnet.meshx2.verified.trust.RealTrustScoreCalculator
import com.ghalbitnet.meshx2.verified.trust.UnifiedProfessionalIdentityCard
import com.ghalbitnet.meshx2.verified.trust.VerifiedIdentityRecord

/**
 * PHASE 282B-282E
 * XML-backed professional card screen for visible APK testing.
 */
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

        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty().ifBlank { "Belum dikenal" }
        val role = intent.getStringExtra(EXTRA_ROLE).orEmpty().ifBlank { "Community Member" }
        val community = intent.getStringExtra(EXTRA_COMMUNITY).orEmpty().ifBlank { "GHALBITNET" }
        val globalId = intent.getStringExtra(EXTRA_GLOBAL_ID).orEmpty().ifBlank { "GX-UNKNOWN" }
        val trustSeed = intent.getIntExtra(EXTRA_TRUST_SCORE, 0)
        val verified = intent.getBooleanExtra(EXTRA_VERIFIED, true)
        val nickname = intent.getStringExtra(EXTRA_NICKNAME).orEmpty()
        val profilePhotoUri = intent.getStringExtra(EXTRA_PROFILE_PHOTO_URI)

        val identity = VerifiedIdentityRecord(
            globalId = globalId,
            publicKeyHash = if (verified) "verified" else "",
            displayName = displayName,
            community = community,
            role = role,
            createdAt = System.currentTimeMillis(),
            identityLevel = if (verified) IdentityLevel.COMMUNITY_VERIFIED else IdentityLevel.UNVERIFIED
        )
        val trustScore = RealTrustScoreCalculator.calculate(identity).coerceAtLeast(trustSeed)
        val summary = ProfessionalCardSummaryFactory.create(
            trustScore = trustScore,
            mentorCount = 0,
            referralActive = 0,
            referralRewarded = 0,
            reputation = CommunityReputationEngine.calculate(0, 0, 0, trustScore / 5)
        )
        val rankLabel = if (summary.trustRank.equals("Pemula", ignoreCase = true)) "Aktif" else summary.trustRank
        val unifiedText = UnifiedProfessionalIdentityCard.render(displayName, community, verified, summary)

        findViewById<TextView>(R.id.txtProfessionalCardTitle).text = "GHALBIT VERIFIED CARD"
        findViewById<TextView>(R.id.txtProfessionalName).text = displayName
        findViewById<TextView>(R.id.txtProfessionalRole).text = role
        findViewById<TextView>(R.id.txtProfessionalCommunity).text = community
        findViewById<TextView>(R.id.txtProfessionalNicknameOrGlobal).text =
            nickname.ifBlank { globalId }
        findViewById<TextView>(R.id.txtProfessionalVerifiedBadge).text = if (verified) "VERIFIED ✓" else "UNVERIFIED"
        findViewById<TextView>(R.id.txtProfessionalTrustBadge).text = "Trust Score: ${summary.trustScore} • Rank: $rankLabel"
        findViewById<TextView>(R.id.txtProfessionalReferralBadge).text = "Referral: ${summary.referralLabel}"
        findViewById<TextView>(R.id.txtProfessionalMentorBadge).text = "Mentor: ${summary.mentorLevel}"
        findViewById<TextView>(R.id.txtProfessionalReputationBadge).text = "Community Reputation: ${summary.communityReputation}"
        findViewById<TextView>(R.id.txtProfessionalUnifiedSummary).text = "$unifiedText\n\nGlobal ID: $globalId"
        bindProfilePhoto(profilePhotoUri, displayName)

        findViewById<Button>(R.id.btnShareProfessionalCard).setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "GHALBIT Verified Card")
                putExtra(Intent.EXTRA_TEXT, "$unifiedText\n\nGlobal ID: $globalId")
            }
            startActivity(Intent.createChooser(shareIntent, "Bagikan Kartu"))
        }
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
        runCatching { img.setImageURI(Uri.parse(photoUri)) }.onFailure { img.setImageDrawable(null) }
        initial.visibility = if (img.drawable != null) View.GONE else View.VISIBLE
    }
}
