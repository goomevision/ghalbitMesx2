package com.ghalbitnet.meshx2.verified.card

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CardStorageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CardDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
}
