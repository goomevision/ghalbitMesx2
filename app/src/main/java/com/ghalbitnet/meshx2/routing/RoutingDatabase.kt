package com.ghalbitnet.meshx2.routing
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RoutingTableEntry::class], version = 1, exportSchema = false)
abstract class RoutingDatabase : RoomDatabase() {
    abstract fun routingDao(): RoutingDao
    companion object {
        @Volatile private var INSTANCE: RoutingDatabase? = null
        fun getInstance(context: Context): RoutingDatabase = INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(context.applicationContext, RoutingDatabase::class.java, "ghalbit_routing")
                .fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}