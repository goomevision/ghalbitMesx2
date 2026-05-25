package com.ghalbitnet.meshx2.access

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CommunitySessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(entity: CommunitySessionEntity)

    @Query("SELECT * FROM community_sessions ORDER BY lastSeen DESC")
    suspend fun getAllSessions(): List<CommunitySessionEntity>

    @Query("SELECT * FROM community_sessions WHERE clientId = :clientId LIMIT 1")
    suspend fun getSession(clientId: String): CommunitySessionEntity?
}
