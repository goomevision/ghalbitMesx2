package com.ghalbitnet.meshx2.verified.screen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.ui.GhalbitTheme
import com.ghalbitnet.meshx2.verified.trust.MentorBadgeRenderer
import com.ghalbitnet.meshx2.verified.trust.ProfessionalCardTrustSummary
import com.ghalbitnet.meshx2.verified.trust.ReferralBadge
import com.ghalbitnet.meshx2.verified.trust.ReferralBadgeRenderer
import com.ghalbitnet.meshx2.verified.trust.TrustRankCalculator
import com.ghalbitnet.meshx2.verified.trust.UnifiedProfessionalIdentityCard

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

        fun createIntent(
            context: Context,
            globalId: String,
            displayName: String,
            role: String,
            community: String,
            trustScore: Int = 0,
            verified: Boolean = true
        ): Intent {
            return Intent(context, ProfessionalCardActivity::class.java).apply {
                putExtra(EXTRA_GLOBAL_ID, globalId)
                putExtra(EXTRA_DISPLAY_NAME, displayName)
                putExtra(EXTRA_ROLE, role)
                putExtra(EXTRA_COMMUNITY, community)
                putExtra(EXTRA_TRUST_SCORE, trustScore)
                putExtra(EXTRA_VERIFIED, verified)
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
        val trustScore = intent.getIntExtra(EXTRA_TRUST_SCORE, 0)
        val verified = intent.getBooleanExtra(EXTRA_VERIFIED, true)

        val summary = ProfessionalCardTrustSummary(
            trustScore = trustScore,
            trustRank = TrustRankCalculator.rank(trustScore),
            mentorLevel = MentorBadgeRenderer.level(studentCount = 0),
            referralLabel = ReferralBadgeRenderer.label(ReferralBadge(activeReferrals = 0, rewardedReferrals = 0)),
            communityReputation = 0
        )
        val unifiedText = UnifiedProfessionalIdentityCard.render(displayName, community, verified, summary)

        findViewById<TextView>(R.id.txtProfessionalCardTitle).text = "GHALBIT VERIFIED CARD"
        findViewById<TextView>(R.id.txtProfessionalName).text = displayName
        findViewById<TextView>(R.id.txtProfessionalRole).text = role
        findViewById<TextView>(R.id.txtProfessionalCommunity).text = community
        findViewById<TextView>(R.id.txtProfessionalVerifiedBadge).text = if (verified) "VERIFIED ✓" else "UNVERIFIED"
        findViewById<TextView>(R.id.txtProfessionalTrustBadge).text = "Trust Score: ${summary.trustScore} • ${summary.trustRank}"
        findViewById<TextView>(R.id.txtProfessionalReferralBadge).text = "Referral: ${summary.referralLabel}"
        findViewById<TextView>(R.id.txtProfessionalMentorBadge).text = "Mentor: ${summary.mentorLevel}"
        findViewById<TextView>(R.id.txtProfessionalUnifiedSummary).text = "$unifiedText\n\nGlobal ID: $globalId"

        findViewById<Button>(R.id.btnShareProfessionalCard).setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "GHALBIT Verified Card")
                putExtra(Intent.EXTRA_TEXT, "$unifiedText\n\nGlobal ID: $globalId")
            }
            startActivity(Intent.createChooser(shareIntent, "Bagikan Kartu"))
        }
    }
}
