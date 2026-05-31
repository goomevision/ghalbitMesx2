package com.ghalbitnet.meshx2.ui.call

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.ghalbitnet.meshx2.chat.ContactListActivity

/**
 * PHASE 246 — Floating Call Button.
 *
 * Lightweight binder that can be attached from MainActivity after setContentView.
 * It adds a persistent call shortcut above the bottom navigation without requiring
 * a risky rewrite of activity_main.xml.
 */
object FloatingCallButtonBinder {
    private const val TAG_VIEW = "GHALBIT_FLOATING_CALL_BUTTON"

    fun attach(activity: Activity) {
        val root = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
        if (findExisting(root) != null) return

        val button = TextView(activity).apply {
            tag = TAG_VIEW
            text = "Telp"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setBackgroundColor(Color.parseColor("#1E7F6F"))
            elevation = dp(activity, 10).toFloat()
            setOnClickListener {
                activity.startActivity(Intent(activity, ContactListActivity::class.java))
            }
        }

        val params = FrameLayout.LayoutParams(dp(activity, 64), dp(activity, 64)).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(0, 0, dp(activity, 18), dp(activity, 86))
        }
        root.addView(button, params)
    }

    private fun findExisting(root: ViewGroup): TextView? {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is TextView && child.tag == TAG_VIEW) return child
            if (child is ViewGroup) {
                val nested = findExisting(child)
                if (nested != null) return nested
            }
        }
        return null
    }

    private fun dp(activity: Activity, value: Int): Int {
        return (value * activity.resources.displayMetrics.density).toInt()
    }
}
