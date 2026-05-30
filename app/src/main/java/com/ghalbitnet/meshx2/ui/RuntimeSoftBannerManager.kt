package com.ghalbitnet.meshx2.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.ghalbitnet.meshx2.R

private data class RuntimeBannerEntry(
    val key: String,
    val title: String,
    val detail: String,
    val priority: Int,
    val durationMs: Long,
    val tone: Int,
    val miniStatus: String? = null,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null
)

class RuntimeSoftBannerManager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private val handler = Handler(Looper.getMainLooper())
    private val panel: View
    private val miniChip: TextView
    private val titleView: TextView
    private val detailView: TextView
    private val miniTextView: TextView
    private val actionButton: Button
    private val lowAnimationMode = LowAnimationMode.enabled(context)
    private val queuedEntries = ArrayDeque<RuntimeBannerEntry>()
    private var hostActive = true
    private var currentEntry: RuntimeBannerEntry? = null
    private var lastEnqueueAt = 0L
    private var lastMiniText = ""
    private var lastRenderKey = ""
    private val dismissRunnable = Runnable { dismissCurrent(showNext = true) }

    init {
        LayoutInflater.from(context).inflate(R.layout.runtime_soft_banner, this, true)
        panel = findViewById(R.id.runtimeSoftBannerPanel)
        miniChip = findViewById(R.id.runtimeSoftMiniChip)
        titleView = findViewById(R.id.runtimeSoftBannerTitle)
        detailView = findViewById(R.id.runtimeSoftBannerDetail)
        miniTextView = miniChip
        actionButton = findViewById(R.id.runtimeSoftBannerAction)
        visibility = GONE
        alpha = 0f
        translationY = -12f
        isClickable = false
        isFocusable = false
    }

    fun render(snapshot: RuntimeUiSnapshot) {
        if (!hostActive || !isAttachedToWindow) return
        updateMiniStatus(snapshot)
        if (shouldUseOverlay(snapshot.state)) {
            return
        }
        val entry = entryFor(snapshot) ?: return
        if (entry.key == lastRenderKey && System.currentTimeMillis() - lastEnqueueAt < 900L) {
            Log.d("GHALBIT-BANNER-QUEUE", "skip duplicate key=${entry.key}")
            return
        }
        enqueue(entry)
    }

    fun showMessage(
        key: String,
        title: String,
        detail: String,
        priority: Int = 3,
        durationMs: Long = 1800L,
        miniStatus: String? = null,
        actionLabel: String? = null,
        action: (() -> Unit)? = null
    ) {
        if (!hostActive || !isAttachedToWindow) return
        enqueue(
            RuntimeBannerEntry(
                key = key,
                title = title,
                detail = detail,
                priority = priority,
                durationMs = durationMs,
                tone = toneForTitle(title, detail),
                miniStatus = miniStatus,
                actionLabel = actionLabel,
                action = action
            )
        )
    }

    private fun enqueue(entry: RuntimeBannerEntry) {
        val now = System.currentTimeMillis()
        if (now - lastEnqueueAt < 700L && currentEntry?.key == entry.key) {
            Log.d("GHALBIT-BANNER-QUEUE", "throttled key=${entry.key}")
            return
        }
        lastEnqueueAt = now
        if (currentEntry == null) {
            showEntry(entry)
            return
        }
        if (currentEntry?.key == entry.key) {
            Log.d("GHALBIT-BANNER-QUEUE", "active duplicate key=${entry.key}")
            return
        }
        queuedEntries.removeAll { it.key == entry.key }
        if ((currentEntry?.priority ?: 0) + 2 <= entry.priority) {
            queuedEntries.addFirst(entry)
            dismissCurrent(showNext = true)
            Log.d("GHALBIT-BANNER-QUEUE", "preempt key=${entry.key}")
            return
        }
        queuedEntries.clear()
        queuedEntries.addLast(entry)
        Log.d("GHALBIT-BANNER-QUEUE", "queued key=${entry.key}")
    }

    private fun showEntry(entry: RuntimeBannerEntry) {
        currentEntry = entry
        lastRenderKey = entry.key
        setTone(entry.tone)
        if (titleView.text != entry.title) titleView.text = entry.title
        if (detailView.text != entry.detail) detailView.text = entry.detail
        if (entry.actionLabel.isNullOrBlank() || entry.action == null) {
            actionButton.visibility = GONE
            actionButton.setOnClickListener(null)
        } else {
            actionButton.visibility = VISIBLE
            actionButton.text = entry.actionLabel
            actionButton.setOnClickListener { entry.action.invoke() }
        }
        if (!entry.miniStatus.isNullOrBlank()) {
            showMiniStatus(entry.miniStatus)
        }
        handler.removeCallbacks(dismissRunnable)
        visibility = VISIBLE
        panel.visibility = VISIBLE
        panel.animate().cancel()
        animate().cancel()
        if (lowAnimationMode) {
            alpha = 0f
            translationY = 0f
            animate().alpha(1f).setDuration(180L).start()
        } else {
            alpha = 0f
            translationY = -14f
            scaleX = 0.985f
            scaleY = 0.985f
            animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(220L).start()
        }
        handler.postDelayed(dismissRunnable, entry.durationMs)
        Log.d("GHALBIT-BANNER", "show key=${entry.key} duration=${entry.durationMs}")
    }

    private fun dismissCurrent(showNext: Boolean) {
        handler.removeCallbacks(dismissRunnable)
        val previous = currentEntry
        currentEntry = null
        if (visibility != VISIBLE) {
            if (showNext) showNextIfNeeded()
            return
        }
        animate().cancel()
        if (lowAnimationMode) {
            animate().alpha(0f).setDuration(180L).withEndAction {
                panel.visibility = GONE
                if (miniChip.visibility != VISIBLE) visibility = GONE
                if (showNext) showNextIfNeeded()
            }.start()
        } else {
            animate().alpha(0f).translationY(-10f).setDuration(200L).withEndAction {
                panel.visibility = GONE
                if (miniChip.visibility != VISIBLE) visibility = GONE
                translationY = 0f
                scaleX = 1f
                scaleY = 1f
                if (showNext) showNextIfNeeded()
            }.start()
        }
        Log.d("GHALBIT-BANNER", "dismiss key=${previous?.key ?: "-"}")
    }

    private fun showNextIfNeeded() {
        if (!hostActive || !isAttachedToWindow) return
        val next = queuedEntries.removeFirstOrNull() ?: return
        showEntry(next)
    }

    private fun updateMiniStatus(snapshot: RuntimeUiSnapshot) {
        val text =
            when (snapshot.state) {
                RuntimeUiState.RECONNECTING -> "Menyambungkan ulang..."
                RuntimeUiState.WEAK_SIGNAL -> "Sinyal jaringan lemah"
                RuntimeUiState.INTERNET_FALLBACK -> "Menggunakan internet"
                RuntimeUiState.OFFLINE_PENDING -> "Menunggu koneksi"
                RuntimeUiState.DISCOVERING -> "Mencari jalur terbaik..."
                RuntimeUiState.READY,
                RuntimeUiState.IDLE -> ""
                else -> snapshot.detail
            }
        if (text == lastMiniText) return
        lastMiniText = text
        if (text.isBlank()) {
            miniChip.animate().cancel()
            miniChip.animate().alpha(0f).setDuration(160L).withEndAction {
                miniChip.visibility = GONE
                if (panel.visibility != VISIBLE) visibility = GONE
            }.start()
        } else {
            showMiniStatus(text)
        }
    }

    private fun showMiniStatus(text: String) {
        miniTextView.text = text
        visibility = VISIBLE
        if (miniChip.visibility != VISIBLE) {
            miniChip.alpha = 0f
            miniChip.visibility = VISIBLE
            miniChip.animate().cancel()
            if (lowAnimationMode) {
                miniChip.animate().alpha(1f).setDuration(160L).start()
            } else {
                miniChip.translationY = -6f
                miniChip.animate().alpha(1f).translationY(0f).setDuration(180L).start()
            }
        }
        Log.d("GHALBIT-BANNER", "mini status=$text")
    }

    private fun entryFor(snapshot: RuntimeUiSnapshot): RuntimeBannerEntry? {
        val duration =
            when (snapshot.state) {
                RuntimeUiState.RECONNECTING -> 2500L
                RuntimeUiState.WEAK_SIGNAL,
                RuntimeUiState.OFFLINE_PENDING,
                RuntimeUiState.FAILED -> 3000L
                RuntimeUiState.READY -> 1500L
                else -> 1800L
            }
        val priority =
            when (snapshot.state) {
                RuntimeUiState.FAILED -> 5
                RuntimeUiState.RECONNECTING -> 4
                RuntimeUiState.OFFLINE_PENDING -> 4
                RuntimeUiState.INTERNET_FALLBACK -> 4
                RuntimeUiState.WEAK_SIGNAL -> 3
                RuntimeUiState.READY -> 2
                RuntimeUiState.DISCOVERING -> 2
                else -> 1
            }
        return when (snapshot.state) {
            RuntimeUiState.DISCOVERING,
            RuntimeUiState.RECONNECTING,
            RuntimeUiState.READY,
            RuntimeUiState.WEAK_SIGNAL,
            RuntimeUiState.INTERNET_FALLBACK,
            RuntimeUiState.OFFLINE_PENDING,
            RuntimeUiState.FAILED ->
                RuntimeBannerEntry(
                    key = "${snapshot.source}:${snapshot.state.name}:${snapshot.detail}",
                    title = snapshot.title,
                    detail = snapshot.detail,
                    priority = priority,
                    durationMs = duration,
                    tone = toneFor(snapshot.state),
                    miniStatus = snapshot.detail
                )
            else -> null
        }
    }

    private fun setTone(color: Int) {
        panel.background?.setTint(color)
        miniChip.background?.setTint(color)
    }

    private fun toneFor(state: RuntimeUiState): Int {
        return when (state) {
            RuntimeUiState.DISCOVERING -> Color.parseColor("#123C5D")
            RuntimeUiState.RECONNECTING -> Color.parseColor("#3B2464")
            RuntimeUiState.READY -> Color.parseColor("#124C37")
            RuntimeUiState.WEAK_SIGNAL -> Color.parseColor("#5F4300")
            RuntimeUiState.INTERNET_FALLBACK -> Color.parseColor("#153B6A")
            RuntimeUiState.OFFLINE_PENDING -> Color.parseColor("#334155")
            RuntimeUiState.FAILED -> Color.parseColor("#5A1D27")
            else -> Color.parseColor("#18263B")
        }
    }

    private fun toneForTitle(title: String, detail: String): Int {
        val source = "$title $detail".lowercase()
        return when {
            "sos" in source -> Color.parseColor("#5A1D27")
            "internet" in source -> Color.parseColor("#153B6A")
            "lokal" in source -> Color.parseColor("#124C37")
            "menunggu" in source || "gagal" in source -> Color.parseColor("#5F4300")
            else -> Color.parseColor("#18263B")
        }
    }

    fun onHostResume() {
        hostActive = true
        Log.d("GHALBIT-UX-PERF", "banner resume")
    }

    fun onHostPause() {
        hostActive = false
        handler.removeCallbacks(dismissRunnable)
        animate().cancel()
        panel.animate().cancel()
        miniChip.animate().cancel()
        Log.d("GHALBIT-UX-PERF", "banner pause")
    }

    fun onHostDestroy() {
        hostActive = false
        handler.removeCallbacksAndMessages(null)
        animate().cancel()
        panel.animate().cancel()
        miniChip.animate().cancel()
        queuedEntries.clear()
        currentEntry = null
        Log.d("GHALBIT-UX-PERF", "banner destroy")
    }

    companion object {
        private fun shouldUseOverlay(state: RuntimeUiState): Boolean {
            return state in setOf(
                RuntimeUiState.PREPARING,
                RuntimeUiState.CONNECTING,
                RuntimeUiState.VERIFYING,
                RuntimeUiState.SYNCING
            )
        }

        fun attach(activity: Activity): RuntimeSoftBannerManager {
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            content.findViewById<RuntimeSoftBannerManager>(R.id.runtimeSoftBanner)?.let { return it }
            return RuntimeSoftBannerManager(activity).apply {
                id = R.id.runtimeSoftBanner
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.TOP
                    }
                content.addView(this)
            }
        }
    }
}
