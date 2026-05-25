package com.ghalbitnet.meshx2.blockchain

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BlockEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BlockDatabase : RoomDatabase() {

    abstract fun blockDao(): BlockDao

    companion object {

        @Volatile
        private var INSTANCE: BlockDatabase? = null

        fun getInstance(
            context: Context
        ): BlockDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BlockDatabase::class.java,
                    "ghalbit_blockchain.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance

                instance
            }
        }
    }
}
