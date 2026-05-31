package com.ghalbitnet.meshx2.activityfeed

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ActivityFeedItem::class],
    version = 1,
    exportSchema = false
)
abstract class ActivityFeedDatabase : RoomDatabase() {
    abstract fun activityFeedDao(): ActivityFeedDao

    companion object {
        @Volatile
        private var INSTANCE: ActivityFeedDatabase? = null

        fun getInstance(context: Context): ActivityFeedDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    ActivityFeedDatabase::class.java,
                    "ghalbit_activity_feed"
                )
                    // Development only. Replace with explicit migration before production release.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
