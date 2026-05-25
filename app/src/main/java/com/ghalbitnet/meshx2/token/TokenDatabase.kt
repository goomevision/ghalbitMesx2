package com.ghalbitnet.meshx2.token
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TokenTransaction::class], version = 1, exportSchema = false)
abstract class TokenDatabase : RoomDatabase() {
    abstract fun tokenDao(): TokenDao
    companion object {
        @Volatile private var INSTANCE: TokenDatabase? = null
        fun getInstance(context: Context): TokenDatabase = INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(context.applicationContext, TokenDatabase::class.java, "ghalbit_ledger")
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
        }
    }
}
