package com.ghalbitnet.meshx2.chat

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ChatMessage::class], version = 5, exportSchema = false)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        fun getInstance(context: Context): ChatDatabase = INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(context.applicationContext, ChatDatabase::class.java, "ghalbit_chat")
                // Development only. Replace with explicit migration before production release.
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
        }
    }
}
