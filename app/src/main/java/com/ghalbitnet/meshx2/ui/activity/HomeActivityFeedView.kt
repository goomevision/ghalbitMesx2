package com.ghalbitnet.meshx2.ui.activity

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * PHASE 247 — Activity Feed.
 *
 * Lightweight reusable Home activity feed. This component is safe to attach
 * from MainActivity without replacing the large activity_main.xml file.
 */
class HomeActivityFeedView(context: Context) : LinearLayout(context) {

    init {
        orientation = VERTICAL
        addView(title("Aktivitas Terbaru"))
        addView(item("Pesan", "Belum ada pesan baru", "Chat akan tampil di sini"))
        addView(item("Panggilan", "Belum ada panggilan", "Riwayat panggilan akan tampil di sini"))
        addView(item("SOS", "Tidak ada SOS aktif", "Sinyal darurat komunitas akan tampil di sini"))
        addView(item("Wallet", "Wallet siap", "Transfer dan reward GHBT akan tampil di sini"))
    }

    private fun title(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.parseColor("#F5FBFF"))
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(14), 0, dp(8))
        }
    }

    private fun item(category: String, headline: String, detail: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.parseColor("#162836"))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(8))
            }
            addView(badge(category.take(1)))
            addView(textBlock(category, headline, detail))
        }
    }

    private fun badge(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundColor(Color.parseColor("#1E7F6F"))
            layoutParams = LayoutParams(dp(38), dp(38))
        }
    }

    private fun textBlock(category: String, headline: String, detail: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(12), 0, 0, 0)
            }
            addView(label(category, 10f, "#8EE3BE", true))
            addView(label(headline, 13f, "#F5FBFF", true))
            addView(label(detail, 10f, "#A9C7D9", false))
        }
    }

    private fun label(text: String, size: Float, color: String, bold: Boolean): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.parseColor(color))
            textSize = size
            maxLines = 1
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
