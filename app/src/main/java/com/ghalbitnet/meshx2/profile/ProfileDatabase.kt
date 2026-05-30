package com.ghalbitnet.meshx2.profile

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MyProfileEntity::class,
        ContactProfileEntity::class,
        ContactAliasEntity::class,
        ProfilePrivacyEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ProfileDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    companion object {
        @Volatile
        private var instance: ProfileDatabase? = null

        fun getInstance(context: Context): ProfileDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ProfileDatabase::class.java,
                    "ghalbit_profile"
                ).fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
