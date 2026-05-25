package com.ghalbitnet.meshx2.vpn

import android.content.Context
import com.ghalbitnet.meshx2.access.ClientTrustEntity
import com.ghalbitnet.meshx2.access.CommunitySessionDao
import com.ghalbitnet.meshx2.access.CommunitySessionEntity
import com.ghalbitnet.meshx2.access.ProviderActionLogEntity
import com.ghalbitnet.meshx2.access.TrustDao
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UsageSessionEntity::class,
        UsageDeltaEntity::class,
        ClientTrustEntity::class,
        ProviderActionLogEntity::class,
        CommunitySessionEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class GhalbitLocalDatabase : RoomDatabase() {

    abstract fun usageDao(): UsageDao
    abstract fun trustDao(): TrustDao
    abstract fun communitySessionDao(): CommunitySessionDao

    companion object {
        @Volatile
        private var INSTANCE: GhalbitLocalDatabase? = null

        fun getInstance(context: Context): GhalbitLocalDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    GhalbitLocalDatabase::class.java,
                    "ghalbit_local_usage"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
