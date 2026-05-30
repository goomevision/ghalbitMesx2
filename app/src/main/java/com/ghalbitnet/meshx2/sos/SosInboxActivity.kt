package com.ghalbitnet.meshx2.sos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.profile.ContactNameCardActivity
import com.ghalbitnet.meshx2.ui.RuntimeSoftBannerManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SosInboxActivity : AppCompatActivity() {
    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private lateinit var runtimeSoftBanner: RuntimeSoftBannerManager

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                render()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sos_inbox)
        runtimeSoftBanner = RuntimeSoftBannerManager.attach(this)
        listView = findViewById(R.id.listSosInbox)
        emptyView = findViewById(R.id.tvSosEmpty)
        render()
    }

    override fun onResume() {
        super.onResume()
        runtimeSoftBanner.onHostResume()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(receiver, IntentFilter(SosAlertManager.ACTION_SOS_UPDATED))
        render()
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        runtimeSoftBanner.onHostPause()
        super.onPause()
    }

    override fun onDestroy() {
        runtimeSoftBanner.onHostDestroy()
        super.onDestroy()
    }

    private fun render() {
        val alerts = SosAlertManager.all(this)
        if (alerts.isEmpty()) {
            emptyView.text = getString(R.string.sos_inbox_empty)
            listView.adapter = null
            return
        }
        emptyView.text = ""
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        alerts.firstOrNull()?.let { latest ->
            runtimeSoftBanner.showMessage(
                key = "sos:inbox:${latest.alertId}",
                title = "SOS masuk dari ${latest.sourceNodeId}",
                detail = latest.message,
                priority = 6,
                durationMs = 4000L,
                miniStatus = "SOS tersimpan"
            )
        }
        listView.adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                alerts.map { alert ->
                    buildString {
                        append(if (alert.isRead) "READ" else "UNREAD")
                        append(" | ")
                        append(alert.sourceNodeId)
                        alert.sourceGlobalId?.let {
                            append(" | ")
                            append(it)
                        }
                        append("\n")
                        append(formatter.format(Date(alert.receivedAt)))
                        append(" | ")
                        append(alert.message)
                        alert.routeHint?.let {
                            append(" | route=")
                            append(it)
                        }
                    }
                }
            )
        listView.setOnItemClickListener { _, _, position, _ ->
            val alert = alerts[position]
            SosAlertManager.markRead(this, alert.alertId)
            alert.sourceGlobalId?.takeIf { it.isNotBlank() }?.let { globalId ->
                startActivity(
                    ContactNameCardActivity.createIntent(
                        context = this,
                        globalId = globalId,
                        chatId = alert.sourceNodeId,
                        fallbackName = alert.sourceNodeId,
                        routeHint = alert.routeHint
                    )
                )
                android.util.Log.d("GHALBIT-CARD", "opened from sos")
            }
            render()
        }
    }
}
