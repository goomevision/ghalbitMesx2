package com.ghalbitnet.meshx2.verified.card

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: CardStorageEntity)

    @Query("SELECT * FROM verified_cards WHERE globalId = :globalId LIMIT 1")
    suspend fun get(globalId: String): CardStorageEntity?

    @Query("SELECT * FROM verified_cards ORDER BY updatedAt DESC")
    suspend fun list(): List<CardStorageEntity>

    @Query("DELETE FROM verified_cards WHERE globalId = :globalId")
    suspend fun deleteByGlobalId(globalId: String)
}
