package com.ghalbitnet.meshx2.verified.screen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ghalbitnet.meshx2.ui.GhalbitTheme

/**
 * PHASE 270A
 * Real preview Activity for GHALBIT verified cards.
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
        title = "Kartu Terverifikasi"

        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty().ifBlank { "Belum dikenal" }
        val role = intent.getStringExtra(EXTRA_ROLE).orEmpty().ifBlank { "Community Member" }
        val community = intent.getStringExtra(EXTRA_COMMUNITY).orEmpty().ifBlank { "GHALBITNET" }
        val globalId = intent.getStringExtra(EXTRA_GLOBAL_ID).orEmpty().ifBlank { "GX-UNKNOWN" }
        val trustScore = intent.getIntExtra(EXTRA_TRUST_SCORE, 0)
        val verified = intent.getBooleanExtra(EXTRA_VERIFIED, true)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(36, 42, 36, 42)
        }

        fun label(text: String, size: Float, bold: Boolean = false): TextView {
            return TextView(this).apply {
                this.text = text
                textSize = size
                gravity = Gravity.CENTER
                if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, 8, 0, 8)
            }
        }

        root.addView(label("GHALBIT VERIFIED CARD", 18f, true))
        root.addView(label(displayName, 24f, true))
        root.addView(label(role, 16f))
        root.addView(label(community, 15f))
        root.addView(label(if (verified) "VERIFIED ✓" else "UNVERIFIED", 15f, true))
        root.addView(label("Trust Score: $trustScore", 14f))
        root.addView(label(globalId, 12f))

        setContentView(root)
    }
}
