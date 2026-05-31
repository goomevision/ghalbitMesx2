package com.ghalbitnet.meshx2.activityfeed

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.dashboard.RuntimeDashboardActivity
import com.ghalbitnet.meshx2.debug.DebugActivity
import com.ghalbitnet.meshx2.monitor.NetworkActivity
import com.ghalbitnet.meshx2.sos.SosInboxActivity
import com.ghalbitnet.meshx2.ui.RuntimeSoftBannerManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActivityFeedActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private lateinit var summaryView: TextView
    private lateinit var filterGroup: RadioGroup
    private lateinit var runtimeSoftBanner: RuntimeSoftBannerManager

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            render(showBanner = false)
            refreshHandler.postDelayed(this, AUTO_REFRESH_MS)
        }
    }

    private var currentCategory: String? = null
    private var renderedItems: List<ActivityFeedItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_activity_feed)

        runtimeSoftBanner = RuntimeSoftBannerManager.attach(this)

        listView = findViewById(R.id.listActivityFeed)
        emptyView = findViewById(R.id.tvActivityFeedEmpty)
        summaryView = findViewById(R.id.tvActivityFeedSummary)
        filterGroup = findViewById(R.id.groupActivityFeedFilter)

        filterGroup.setOnCheckedChangeListener { _, checkedId ->
            currentCategory = when (checkedId) {
                R.id.filterChat -> "CHAT"
                R.id.filterCall -> "CALL"
                R.id.filterSos -> "SOS"
                R.id.filterNetwork -> "NETWORK"
                R.id.filterSecurity -> "SECURITY"
                R.id.filterRuntime -> "RUNTIME"
                else -> null
            }
            render(showBanner = true)
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            renderedItems.getOrNull(position)?.let { openActionFor(it) }
        }

        render(showBanner = true)
    }

    override fun onResume() {
        super.onResume()
        runtimeSoftBanner.onHostResume()
        render(showBanner = true)
        refreshHandler.removeCallbacks(refreshRunnable)
        refreshHandler.postDelayed(refreshRunnable, AUTO_REFRESH_MS)
    }

    override fun onPause() {
        refreshHandler.removeCallbacks(refreshRunnable)
        runtimeSoftBanner.onHostPause()
        super.onPause()
    }

    override fun onDestroy() {
        refreshHandler.removeCallbacks(refreshRunnable)
        runtimeSoftBanner.onHostDestroy()
        super.onDestroy()
    }

    private fun render(showBanner: Boolean) {
        val repository = ActivityFeedRepository(this)
        val allItems = repository.latest(500)

        val items = if (currentCategory == null) {
            allItems
        } else {
            allItems.filter { it.category.equals(currentCategory, ignoreCase = true) }
        }
        renderedItems = items

        val filterLabel = currentCategory ?: "ALL"
        summaryView.text = "$filterLabel • ${items.size} ditampilkan • total ${repository.totalCount()} event • auto refresh ${AUTO_REFRESH_MS / 1000}s"

        if (items.isEmpty()) {
            emptyView.text = "Belum ada aktivitas pada filter $filterLabel"
            listView.adapter = null
            return
        }

        emptyView.text = ""

        val formatter = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())

        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            items.map { item -> formatItem(item, formatter) }
        )

        if (showBanner) {
            items.firstOrNull()?.let {
                runtimeSoftBanner.showMessage(
                    key = "activity-feed-latest:${it.id}",
                    title = it.title,
                    detail = it.message,
                    priority = 4,
                    durationMs = 2500L,
                    miniStatus = it.category
                )
            }
        }
    }

    private fun formatItem(
        item: ActivityFeedItem,
        formatter: SimpleDateFormat
    ): String {
        val icon = when (item.category.uppercase()) {
            "CHAT" -> "💬"
            "CALL" -> "📞"
            "SOS" -> "🆘"
            "NETWORK" -> "📡"
            "SECURITY" -> "🛡️"
            "RUNTIME" -> "⚙️"
            "WALLET" -> "💰"
            else -> "•"
        }
        return buildString {
            append(icon)
            append("  ")
            append(item.title)
            append("\n")
            append(formatter.format(Date(item.timestamp)))
            append(" • ")
            append(item.category)
            append(" • ")
            append(item.severity)
            append("\n")
            append(item.message)
            item.peerId?.takeIf { it.isNotBlank() }?.let {
                append("\npeer: ")
                append(it)
            }
        }
    }

    private fun openActionFor(item: ActivityFeedItem) {
        when (item.category.uppercase()) {
            "SOS" -> startActivity(Intent(this, SosInboxActivity::class.java))
            "NETWORK" -> startActivity(Intent(this, NetworkActivity::class.java))
            "RUNTIME" -> startActivity(Intent(this, RuntimeDashboardActivity::class.java))
            "SECURITY" -> startActivity(Intent(this, DebugActivity::class.java))
            "CALL" -> runtimeSoftBanner.showMessage(
                key = "activity-action-call:${item.id}",
                title = "Detail panggilan",
                detail = item.message,
                priority = 4,
                durationMs = 3000L,
                miniStatus = "CALL"
            )
            "CHAT" -> runtimeSoftBanner.showMessage(
                key = "activity-action-chat:${item.id}",
                title = "Riwayat chat",
                detail = "Buka daftar kontak/chat untuk melihat percakapan terkait ${item.peerId ?: "peer"}.",
                priority = 4,
                durationMs = 3000L,
                miniStatus = "CHAT"
            )
            else -> runtimeSoftBanner.showMessage(
                key = "activity-action:${item.id}",
                title = item.title,
                detail = item.message,
                priority = 3,
                durationMs = 3000L,
                miniStatus = item.category
            )
        }
    }

    companion object {
        private const val AUTO_REFRESH_MS = 6_000L
    }
}
