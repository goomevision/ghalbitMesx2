package com.ghalbitnet.meshx2.activityfeed

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ghalbitnet.meshx2.R
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

    private var currentCategory: String? = null

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
            render()
        }

        render()
    }

    override fun onResume() {
        super.onResume()
        runtimeSoftBanner.onHostResume()
        render()
    }

    override fun onPause() {
        runtimeSoftBanner.onHostPause()
        super.onPause()
    }

    override fun onDestroy() {
        runtimeSoftBanner.onHostDestroy()
        super.onDestroy()
    }

    private fun render() {
        val repository = ActivityFeedRepository(this)
        val allItems = repository.latest(500)

        val items = if (currentCategory == null) {
            allItems
        } else {
            allItems.filter { it.category.equals(currentCategory, ignoreCase = true) }
        }

        summaryView.text = "${items.size} aktivitas ditampilkan • total ${repository.totalCount()} event"

        if (items.isEmpty()) {
            emptyView.text = "Belum ada aktivitas pada filter yang dipilih"
            listView.adapter = null
            return
        }

        emptyView.text = ""

        val formatter = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())

        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            items.map {
                buildString {
                    append(formatter.format(Date(it.timestamp)))
                    append("\n")
                    append(it.title)
                    append("\n")
                    append(it.message)
                    append("\n[")
                    append(it.category)
                    append("]")
                }
            }
        )

        items.firstOrNull()?.let {
            runtimeSoftBanner.showMessage(
                key = "activity-feed-latest",
                title = it.title,
                detail = it.message,
                priority = 4,
                durationMs = 2500L,
                miniStatus = it.category
            )
        }
    }
}
