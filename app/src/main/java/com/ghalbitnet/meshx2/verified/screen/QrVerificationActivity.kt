package com.ghalbitnet.meshx2.verified.screen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ghalbitnet.meshx2.ui.GhalbitTheme

class QrVerificationActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA_PAYLOAD = "verified.qrPayload"

        fun createIntent(context: Context, payload: String): Intent {
            return Intent(context, QrVerificationActivity::class.java).apply {
                putExtra(EXTRA_PAYLOAD, payload)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GhalbitTheme.applyWindow(this, "verified-qr")
        title = "Verifikasi Kartu"

        val payload = intent.getStringExtra(EXTRA_PAYLOAD).orEmpty()
        val status = if (payload.isNotBlank()) "VALID PAYLOAD" else "EMPTY PAYLOAD"

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

        root.addView(label("GHALBIT QR VERIFICATION", 18f, true))
        root.addView(label(status, 16f, true))
        root.addView(label(payload.take(220), 12f))
        setContentView(root)
    }
}
