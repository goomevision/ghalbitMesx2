package com.ghalbitnet.meshx2.chat

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DraftMessageEntity::class,
        DraftAttachmentEntity::class,
        MessageEditEntity::class,
        MessageDeleteEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DraftDatabase : RoomDatabase() {
    abstract fun draftMessageDao(): DraftMessageDao

    companion object {
        @Volatile
        private var instance: DraftDatabase? = null

        fun getInstance(context: Context): DraftDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DraftDatabase::class.java,
                    "ghalbit_draft"
                ).build().also { instance = it }
            }
    }
}
