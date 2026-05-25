package com.ghalbitnet.meshx2.access

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrustDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClientTrust(entity: ClientTrustEntity)

    @Query("SELECT * FROM client_trust WHERE clientIp = :clientIp LIMIT 1")
    suspend fun getClientTrust(clientIp: String): ClientTrustEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionLog(entity: ProviderActionLogEntity): Long

    @Query("SELECT * FROM provider_action_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentActions(limit: Int): List<ProviderActionLogEntity>
}
