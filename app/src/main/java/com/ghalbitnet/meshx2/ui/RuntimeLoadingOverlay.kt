package com.ghalbitnet.meshx2.ui

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.ghalbitnet.meshx2.R

class RuntimeLoadingOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private val panel: View
    private val orb: View
    private val ring: View
    private val dot1: View
    private val dot2: View
    private val dot3: View
    private val txtTitle: TextView
    private val txtDetail: TextView
    private var pulseAnimator: ObjectAnimator? = null
    private val lowAnimationMode = LowAnimationMode.enabled(context)
    private var currentState: RuntimeUiState? = null
    private var currentTone: Int? = null
    private var hostActive = true
    private var lastRenderAt = 0L
    private var lastShouldShow = false

    init {
        LayoutInflater.from(context).inflate(R.layout.runtime_loading_overlay, this, true)
        panel = findViewById(R.id.runtimeOverlayPanel)
        orb = findViewById(R.id.runtimeOverlayOrb)
        ring = findViewById(R.id.runtimeOverlayRing)
        dot1 = findViewById(R.id.runtimeOverlayDot1)
        dot2 = findViewById(R.id.runtimeOverlayDot2)
        dot3 = findViewById(R.id.runtimeOverlayDot3)
        txtTitle = findViewById(R.id.runtimeOverlayTitle)
        txtDetail = findViewById(R.id.runtimeOverlayDetail)
        alpha = 0f
        visibility = GONE
        isClickable = false
        setWillNotDraw(true)
    }

    fun render(snapshot: RuntimeUiSnapshot) {
        val now = System.currentTimeMillis()
        val shouldShow =
            snapshot.state in setOf(
                RuntimeUiState.PREPARING,
                RuntimeUiState.CONNECTING,
                RuntimeUiState.VERIFYING,
                RuntimeUiState.SYNCING
            )
        val sameState = currentState == snapshot.state
        val sameText = txtTitle.text == snapshot.title && txtDetail.text == snapshot.detail
        if (sameState && sameText && shouldShow == lastShouldShow && now - lastRenderAt < 250L) {
            Log.d("GHALBIT-UX-PERF", "overlay render throttled state=${snapshot.state}")
            return
        }
        if (txtTitle.text != snapshot.title) txtTitle.text = snapshot.title
        if (txtDetail.text != snapshot.detail) txtDetail.text = snapshot.detail
        val tone = toneFor(snapshot.state)
        if (currentTone != tone) {
            orb.setBackgroundColor(tone)
            ring.background?.setTint(tone)
            listOf(dot1, dot2, dot3).forEach { it.background?.setTint(tone) }
            currentTone = tone
        }

        if (!hostActive) {
            stopBreathing()
            currentState = snapshot.state
            lastShouldShow = shouldShow
            lastRenderAt = now
            Log.d("GHALBIT-UX-PERF", "overlay host paused state=${snapshot.state}")
            return
        }

        if (shouldShow) {
            if (visibility != VISIBLE) {
                clearAnimation()
                animate().cancel()
                alpha = 0f
                visibility = VISIBLE
                animate().alpha(1f).setDuration(180L).start()
            }
            if (!sameState || pulseAnimator == null) {
                startBreathing(snapshot.state)
            }
        } else {
            stopBreathing()
            if (visibility != GONE) {
                clearAnimation()
                animate().cancel()
                animate().alpha(0f).setDuration(180L).withEndAction { visibility = GONE }.start()
            }
        }
        currentState = snapshot.state
        lastShouldShow = shouldShow
        lastRenderAt = now
        Log.d("GHALBIT-LOADING", "state=${snapshot.state} visible=$shouldShow")
    }

    private fun startBreathing(state: RuntimeUiState) {
        if (!hostActive) return
        stopBreathing()
        val duration =
            when (state) {
                RuntimeUiState.RECONNECTING -> 1400L
                RuntimeUiState.WEAK_SIGNAL -> 1600L
                RuntimeUiState.DISCOVERING -> 1900L
                RuntimeUiState.CONNECTING -> 1700L
                RuntimeUiState.INTERNET_FALLBACK -> 1800L
                else -> 2000L
            }
        if (lowAnimationMode) {
            orb.alpha = 0.92f
            ring.alpha = 0.16f
            dot1.alpha = 0.42f
            dot2.alpha = 0.24f
            dot3.alpha = 0.16f
            Log.d("GHALBIT-ANIMATION", "low mode state=$state")
            return
        }
        orb.alpha = 1f
        ring.alpha = 0.14f
        dot1.alpha = 0.36f
        dot2.alpha = 0.22f
        dot3.alpha = 0.14f
        pulseAnimator =
            ObjectAnimator.ofFloat(panel, View.ALPHA, 0.94f, 1f, 0.94f).apply {
                this.duration = duration
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
                start()
            }
        Log.d("GHALBIT-ANIMATION", "full mode state=$state")
    }

    private fun stopBreathing() {
        orb.animate().cancel()
        ring.animate().cancel()
        listOf(dot1, dot2, dot3).forEach { it.animate().cancel() }
        pulseAnimator?.cancel()
        pulseAnimator = null
        panel.alpha = 1f
        orb.scaleX = 1f
        orb.scaleY = 1f
        ring.scaleX = 1f
        ring.scaleY = 1f
        ring.alpha = 0.18f
        dot1.alpha = 0.5f
        dot2.alpha = 0.35f
        dot3.alpha = 0.2f
    }

    private fun toneFor(state: RuntimeUiState): Int {
        return when (state) {
            RuntimeUiState.DISCOVERING -> Color.parseColor("#66D9FF")
            RuntimeUiState.CONNECTING -> Color.parseColor("#7FE7FF")
            RuntimeUiState.VERIFYING -> Color.parseColor("#99F6E4")
            RuntimeUiState.SYNCING -> Color.parseColor("#A5F3FC")
            RuntimeUiState.RECONNECTING -> Color.parseColor("#C4B5FD")
            RuntimeUiState.READY -> Color.parseColor("#34D399")
            RuntimeUiState.WEAK_SIGNAL -> Color.parseColor("#FBBF24")
            RuntimeUiState.INTERNET_FALLBACK -> Color.parseColor("#60A5FA")
            RuntimeUiState.OFFLINE_PENDING -> Color.parseColor("#94A3B8")
            RuntimeUiState.FAILED -> Color.parseColor("#FB7185")
            else -> Color.parseColor("#9CA3AF")
        }
    }

    companion object {
        fun attach(activity: Activity): RuntimeLoadingOverlay {
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            content.findViewById<RuntimeLoadingOverlay>(R.id.runtimeLoadingOverlay)?.let { return it }
            return RuntimeLoadingOverlay(activity).apply {
                id = R.id.runtimeLoadingOverlay
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ).apply {
                        gravity = Gravity.TOP
                    }
                content.addView(this)
            }
        }
    }

    fun onHostResume() {
        hostActive = true
        Log.d("GHALBIT-UX-PERF", "overlay resume")
    }

    fun onHostPause() {
        hostActive = false
        stopBreathing()
        animate().cancel()
        Log.d("GHALBIT-UX-PERF", "overlay pause")
    }

    fun onHostDestroy() {
        hostActive = false
        stopBreathing()
        animate().cancel()
        clearAnimation()
        Log.d("GHALBIT-UX-PERF", "overlay destroy")
    }
}
