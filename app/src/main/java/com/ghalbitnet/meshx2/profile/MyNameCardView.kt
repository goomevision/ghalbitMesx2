package com.ghalbitnet.meshx2.profile

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.ghalbitnet.meshx2.R

class MyNameCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private var verifiedPulseAnimator: android.animation.ObjectAnimator? = null
    private var qrGlowAnimator: android.animation.ObjectAnimator? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_my_name_card, this, true)
    }

    fun render(profile: CommunityProfile, routeBadge: String, qrBitmap: Bitmap?) {
        ContactCardRenderer.bind(this, profile, routeBadge, qrBitmap)
        playLiveCardEntry()
        playBadgeReveal()
        playQrGlow()
        playVerifiedPulse()
    }

    private fun playLiveCardEntry() {
        scaleX = 0.985f
        scaleY = 0.985f
        alpha = 0.94f
        animate().cancel()
        animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(220L)
            .start()
    }

    private fun playBadgeReveal() {
        val trust = findViewById<TextView>(R.id.txtCardTrust) ?: return
        val mentor = findViewById<TextView>(R.id.txtCardMentorBadge) ?: return
        val referral = findViewById<TextView>(R.id.txtCardReferralBadge) ?: return
        val reputation = findViewById<TextView>(R.id.txtCardReputationBadge) ?: return
        listOf(trust, mentor, referral, reputation).forEachIndexed { idx, view ->
            view.alpha = 0f
            view.translationY = 6f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((idx * 40).toLong())
                .setDuration(170L)
                .start()
        }
    }

    private fun playQrGlow() {
        val qr = findViewById<ImageView>(R.id.imgCardQr) ?: return
        qrGlowAnimator?.cancel()
        qrGlowAnimator =
            android.animation.ObjectAnimator.ofFloat(qr, View.ALPHA, 0.92f, 1f, 0.92f).apply {
                duration = 2200L
                repeatCount = android.animation.ValueAnimator.INFINITE
                repeatMode = android.animation.ValueAnimator.RESTART
                start()
            }
    }

    private fun playVerifiedPulse() {
        val badge = findViewById<TextView>(R.id.txtVerifiedBadge) ?: return
        val isVerified = badge.text?.contains("VERIFIED", ignoreCase = true) == true
        verifiedPulseAnimator?.cancel()
        if (!isVerified) return
        verifiedPulseAnimator =
            android.animation.ObjectAnimator.ofFloat(badge, View.SCALE_X, 1f, 1.03f, 1f).apply {
                duration = 1800L
                repeatCount = android.animation.ValueAnimator.INFINITE
                repeatMode = android.animation.ValueAnimator.RESTART
                start()
            }
        badge.animate()
            .scaleY(1.03f)
            .setDuration(900L)
            .withEndAction {
                badge.animate().scaleY(1f).setDuration(900L).start()
            }
            .start()
    }

    override fun onDetachedFromWindow() {
        verifiedPulseAnimator?.cancel()
        qrGlowAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}
