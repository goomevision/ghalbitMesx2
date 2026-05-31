package com.ghalbitnet.meshx2.ui.contact

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * PHASE 245 — Contact Carousel.
 *
 * Reusable lightweight view for showing favorite/active contacts on Home.
 * It is intentionally code-only so it can be attached safely from MainActivity
 * without replacing the large activity_main.xml file.
 */
class HomeContactCarouselView(context: Context) : LinearLayout(context) {

    init {
        orientation = VERTICAL
        addView(title("Kontak Favorit"))
        addView(carousel())
    }

    private fun title(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.parseColor("#F5FBFF"))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(14), 0, dp(8))
        }
    }

    private fun carousel(): HorizontalScrollView {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(card("RK", "Kontak 1", "Siap"))
            addView(card("TM", "Tim", "Komunitas"))
            addView(card("GX", "Node", "Cari sekitar"))
            addView(card("+", "Tambah", "Kontak"))
        }
        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            addView(row)
        }
    }

    private fun card(initial: String, name: String, status: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(Color.parseColor("#162836"))
            layoutParams = LayoutParams(dp(118), dp(112)).apply {
                setMargins(0, 0, dp(8), 0)
            }
            addView(badge(initial))
            addView(label(name, true))
            addView(label(status, false))
        }
    }

    private fun badge(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setBackgroundColor(Color.parseColor("#1E7F6F"))
            layoutParams = LayoutParams(dp(42), dp(42))
        }
    }

    private fun label(text: String, bold: Boolean): TextView {
        return TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(if (bold) Color.parseColor("#F5FBFF") else Color.parseColor("#A9C7D9"))
            textSize = if (bold) 12f else 10f
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(7), 0, 0)
            }
            maxLines = 1
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
