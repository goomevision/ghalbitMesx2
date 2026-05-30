package com.ghalbitnet.meshx2.online

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PendingMessageEntity::class,
        PendingReceiptEntity::class,
        PendingMediaEntity::class,
        RetryScheduleEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PendingDatabase : RoomDatabase() {
    abstract fun pendingMessageDao(): PendingMessageDao

    companion object {
        @Volatile
        private var instance: PendingDatabase? = null

        fun getInstance(context: Context): PendingDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PendingDatabase::class.java,
                    "ghalbit_pending"
                ).build().also { instance = it }
            }
    }
}
