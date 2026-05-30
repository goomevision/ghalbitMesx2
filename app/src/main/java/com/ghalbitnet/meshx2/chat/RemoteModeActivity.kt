package com.ghalbitnet.meshx2.chat

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class RemoteModeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO stabilization: restore full remote-mode screen after the legacy
        // contact/presence modules are reattached inside the app source tree.
        val messageView =
            TextView(this).apply {
                text =
                    "Fitur ini sedang disiapkan.\n\nRemote mode akan aktif setelah server relay dan remote presence stabil.\n\nSaat ini aplikasi tetap fokus pada mesh lokal, discovery, dan monitoring inti."
                setPadding(48, 48, 48, 48)
            }

        setContentView(messageView)
    }
}
