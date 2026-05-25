package com.ghalbitnet.meshx2.settings

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.core.utils.AppNotificationManager

class NotificationSettingsActivity : AppCompatActivity() {

    private lateinit var switchChat: SwitchCompat
    private lateinit var switchMedia: SwitchCompat
    private lateinit var switchSos: SwitchCompat
    private lateinit var switchCall: SwitchCompat
    private lateinit var switchQuietChat: SwitchCompat
    private lateinit var txtSystemHint: TextView
    private lateinit var btnSystemSettings: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_settings)

        switchChat = findViewById(R.id.switchChatNotifications)
        switchMedia = findViewById(R.id.switchMediaNotifications)
        switchSos = findViewById(R.id.switchSosNotifications)
        switchCall = findViewById(R.id.switchCallNotifications)
        switchQuietChat = findViewById(R.id.switchQuietChat)
        txtSystemHint = findViewById(R.id.txtNotificationSystemHint)
        btnSystemSettings = findViewById(R.id.btnOpenSystemNotifications)

        bindState()
        bindActions()
    }

    private fun bindState() {
        switchChat.isChecked = AppNotificationManager.isChatEnabled(this)
        switchMedia.isChecked = AppNotificationManager.isMediaEnabled(this)
        switchSos.isChecked = AppNotificationManager.isSosEnabled(this)
        switchCall.isChecked = AppNotificationManager.isCallEnabled(this)
        switchQuietChat.isChecked = AppNotificationManager.isChatQuiet(this)

        txtSystemHint.text =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getString(R.string.notification_settings_system_hint)
            } else {
                getString(R.string.notification_settings_system_hint_legacy)
            }
    }

    private fun bindActions() {
        switchChat.setOnCheckedChangeListener { _, isChecked ->
            AppNotificationManager.setChatEnabled(this, isChecked)
        }

        switchMedia.setOnCheckedChangeListener { _, isChecked ->
            AppNotificationManager.setMediaEnabled(this, isChecked)
        }

        switchSos.setOnCheckedChangeListener { _, isChecked ->
            AppNotificationManager.setSosEnabled(this, isChecked)
        }

        switchCall.setOnCheckedChangeListener { _, isChecked ->
            AppNotificationManager.setCallEnabled(this, isChecked)
        }

        switchQuietChat.setOnCheckedChangeListener { _, isChecked ->
            AppNotificationManager.setChatQuiet(this, isChecked)
        }

        btnSystemSettings.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
            )
        }
    }
}
