package com.ghalbitnet.meshx2.activityfeed

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "activity_feed_items",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["type"]),
        Index(value = ["category"]),
        Index(value = ["peerId"]),
        Index(value = ["source"])
    ]
)
data class ActivityFeedItem(
    @PrimaryKey
    val id: String,
    val timestamp: Long,
    val type: String,
    val category: String,
    val title: String,
    val message: String,
    val peerId: String? = null,
    val source: String? = null,
    val metadata: String? = null,
    val isRead: Boolean = false,
    val severity: String = ActivityFeedSeverity.INFO.name
)

enum class ActivityFeedSeverity {
    INFO,
    SUCCESS,
    WARNING,
    DANGER
}
