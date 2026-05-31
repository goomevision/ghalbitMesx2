package com.ghalbitnet.meshx2.activityfeed

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ActivityFeedDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: ActivityFeedItem)

    @Query("SELECT * FROM activity_feed_items ORDER BY timestamp DESC LIMIT :limit")
    fun latest(limit: Int = 200): List<ActivityFeedItem>

    @Query("DELETE FROM activity_feed_items WHERE timestamp < :cutoff")
    fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM activity_feed_items WHERE id NOT IN (SELECT id FROM activity_feed_items ORDER BY timestamp DESC LIMIT :keepCount)")
    fun trimToLimit(keepCount: Int)

    @Query("SELECT COUNT(*) FROM activity_feed_items")
    fun count(): Int
}
