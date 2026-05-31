package com.ghalbitnet.meshx2.activityfeed

import android.content.Context
import java.util.UUID
import java.util.concurrent.Executors

object ActivityFeedManager {

    private val executor = Executors.newSingleThreadExecutor()
    @Volatile
    private var appContext: Context? = null

    fun bind(context: Context) {
        appContext = context.applicationContext
    }

    fun publish(
        type: ActivityFeedType,
        title: String,
        message: String,
        peerId: String? = null,
        source: String? = null,
        metadata: String? = null
    ) {
        val context = appContext ?: return

        executor.execute {
            runCatching {
                val dao = ActivityFeedDatabase.getInstance(context).activityFeedDao()

                dao.insert(
                    ActivityFeedItem(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        type = type.name,
                        category = type.category().name,
                        title = title,
                        message = message,
                        peerId = peerId,
                        source = source,
                        metadata = metadata
                    )
                )

                val retentionDays = 90L
                dao.deleteOlderThan(System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L))
                dao.trimToLimit(5000)
            }
        }
    }
}
