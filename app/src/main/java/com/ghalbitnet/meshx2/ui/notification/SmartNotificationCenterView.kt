package com.ghalbitnet.meshx2.ui.notification

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.TextView

/** PHASE 248 Smart Notification Center */
class SmartNotificationCenterView(context: Context) : LinearLayout(context) {

    init {
        orientation = VERTICAL
        addView(header())
        addView(notification("Pesan Baru", "Tidak ada pesan yang belum dibaca", "INFO"))
        addView(notification("Panggilan", "Tidak ada panggilan masuk", "CALL"))
        addView(notification("SOS", "Tidak ada peringatan darurat", "SOS"))
        addView(notification("Wallet", "Tidak ada transaksi baru", "GHBT"))
        addView(notification("Node", "Jaringan stabil", "MESH"))
    }

    private fun header(): TextView = TextView(context).apply {
        text = "Pusat Notifikasi"
        setTextColor(Color.parseColor("#F5FBFF"))
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun notification(title:String, detail:String, tagText:String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(12,10,12,10)
            addView(TextView(context).apply {
                text = tagText
                setTextColor(Color.parseColor("#8EE3BE"))
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(12,0,0,0)
                addView(TextView(context).apply {
                    text = title
                    setTextColor(Color.WHITE)
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(TextView(context).apply {
                    text = detail
                    setTextColor(Color.parseColor("#A9C7D9"))
                    textSize = 11f
                })
            })
        }
    }
}
