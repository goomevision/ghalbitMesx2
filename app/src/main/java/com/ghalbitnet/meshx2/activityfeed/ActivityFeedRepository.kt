package com.ghalbitnet.meshx2.activityfeed

import android.content.Context

class ActivityFeedRepository(context: Context) {

    private val dao =
        ActivityFeedDatabase
            .getInstance(context)
            .activityFeedDao()

    fun latest(limit: Int = 200): List<ActivityFeedItem> {
        return dao.latest(limit)
    }

    fun totalCount(): Int {
        return dao.count()
    }
}
