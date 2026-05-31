package com.ghalbitnet.meshx2.ui.dashboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** PHASE 249 Professional Dashboard Header */
class ProfessionalDashboardHeaderView(context: Context) : LinearLayout(context) {

    private val nameView = label("GHALBITNET", 24f, "#F5FBFF", true)
    private val statusView = label("Online • Mesh siap", 12f, "#8EE3BE", true)
    private val identityView = label("GX-UNKNOWN", 11f, "#FFD54F", false)
    private val nodeView = label("Node aktif: 0", 11f, "#A9C7D9", false)

    init {
        orientation = VERTICAL
        setPadding(16, 16, 16, 16)
        setBackgroundColor(Color.parseColor("#102331"))
        addView(rowHeader())
        addView(identityView)
        addView(nodeView)
        addView(shortcutRow())
    }

    fun render(displayName: String, globalId: String, nodeCount: Int, online: Boolean) {
        nameView.text = displayName.ifBlank { "GHALBITNET" }
        identityView.text = globalId.ifBlank { "GX-UNKNOWN" }
        nodeView.text = "Node aktif: $nodeCount"
        statusView.text = if (online) "Online • Mesh siap" else "Offline • Menunggu jaringan"
        statusView.setTextColor(Color.parseColor(if (online) "#8EE3BE" else "#A9C7D9"))
    }

    private fun rowHeader(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(avatar())
            addView(LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(12, 0, 0, 0)
                addView(nameView)
                addView(statusView)
            })
        }
    }

    private fun avatar(): TextView {
        return TextView(context).apply {
            text = "GX"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundColor(Color.parseColor("#1E7F6F"))
            layoutParams = LayoutParams(48, 48)
        }
    }

    private fun shortcutRow(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(0, 12, 0, 0)
            addView(shortcut("Chat"))
            addView(shortcut("Call"))
            addView(shortcut("Wallet"))
            addView(shortcut("SOS"))
        }
    }

    private fun shortcut(textValue: String): TextView {
        return TextView(context).apply {
            text = textValue
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundColor(Color.parseColor("#162836"))
            layoutParams = LayoutParams(0, 42, 1f).apply { setMargins(3, 0, 3, 0) }
        }
    }

    private fun label(textValue: String, size: Float, color: String, bold: Boolean): TextView {
        return TextView(context).apply {
            text = textValue
            textSize = size
            setTextColor(Color.parseColor(color))
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
    }
}
