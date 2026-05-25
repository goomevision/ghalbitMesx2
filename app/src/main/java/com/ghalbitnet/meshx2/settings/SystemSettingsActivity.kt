package com.ghalbitnet.meshx2.settings

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.chat.RemoteModeActivity
import com.ghalbitnet.meshx2.core.utils.SafeNavigator
import com.ghalbitnet.meshx2.debug.DebugActivity
import com.ghalbitnet.meshx2.monitor.NetworkActivity

class SystemSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_system_settings)

        findViewById<Button>(R.id.btnSystemNotifications).setOnClickListener {
            SafeNavigator.open(
                this,
                NotificationSettingsActivity::class.java,
                getString(R.string.system_settings_notification_unavailable)
            )
        }

        findViewById<Button>(R.id.btnSystemMedia).setOnClickListener {
            SafeNavigator.open(
                this,
                ChatMediaSettingsActivity::class.java,
                getString(R.string.system_settings_media_unavailable)
            )
        }

        findViewById<Button>(R.id.btnSystemInternetSharing).setOnClickListener {
            SafeNavigator.open(
                this,
                InternetSharingSettingsActivity::class.java,
                getString(R.string.system_settings_sharing_unavailable)
            )
        }

        findViewById<Button>(R.id.btnSystemRoles).setOnClickListener {
            startActivity(
                android.content.Intent(this, OnboardingActivity::class.java).apply {
                    putExtra(OnboardingActivity.EXTRA_SETTINGS_MODE, true)
                }
            )
        }

        findViewById<Button>(R.id.btnSystemRemote).setOnClickListener {
            SafeNavigator.open(
                this,
                RemoteModeActivity::class.java,
                getString(R.string.system_settings_remote_unavailable)
            )
        }

        findViewById<Button>(R.id.btnSystemNetwork).setOnClickListener {
            SafeNavigator.open(
                this,
                NetworkActivity::class.java,
                getString(R.string.system_settings_network_unavailable)
            )
        }

        findViewById<Button>(R.id.btnSystemDebug).setOnClickListener {
            SafeNavigator.open(
                this,
                DebugActivity::class.java,
                getString(R.string.system_settings_debug_unavailable)
            )
        }
    }
}
