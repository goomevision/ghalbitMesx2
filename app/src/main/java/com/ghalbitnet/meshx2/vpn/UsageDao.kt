package com.ghalbitnet.meshx2.vpn

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UsageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: UsageSessionEntity)

    @Query(
        """
        UPDATE usage_sessions SET
            totalUploadBytes = :totalUploadBytes,
            totalDownloadBytes = :totalDownloadBytes,
            totalBytes = :totalBytes,
            packetCount = :packetCount,
            tcpCount = :tcpCount,
            udpCount = :udpCount,
            icmpCount = :icmpCount,
            ipv6Count = :ipv6Count,
            unknownCount = :unknownCount,
            operatingMode = :operatingMode,
            providerNodeId = :providerNodeId,
            gatewayNodeId = :gatewayNodeId,
            updatedAt = :updatedAt
        WHERE sessionId = :sessionId
        """
    )
    suspend fun updateSessionTotals(
        sessionId: String,
        totalUploadBytes: Long,
        totalDownloadBytes: Long,
        totalBytes: Long,
        packetCount: Long,
        tcpCount: Long,
        udpCount: Long,
        icmpCount: Long,
        ipv6Count: Long,
        unknownCount: Long,
        operatingMode: String,
        providerNodeId: String?,
        gatewayNodeId: String?,
        updatedAt: Long
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDelta(delta: UsageDeltaEntity): Long

    @Query("SELECT * FROM usage_sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveSession(): UsageSessionEntity?

    @Query("SELECT * FROM usage_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: String): UsageSessionEntity?

    @Query("SELECT * FROM usage_sessions ORDER BY startTime DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int): List<UsageSessionEntity>

    @Query("SELECT * FROM usage_deltas WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    suspend fun getSessionDeltas(sessionId: String): List<UsageDeltaEntity>

    @Query("UPDATE usage_sessions SET endTime = :endTime, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun closeSession(
        sessionId: String,
        endTime: Long,
        updatedAt: Long
    )

    @Query("SELECT * FROM usage_sessions WHERE isSynced = 0 ORDER BY createdAt ASC")
    suspend fun getUnsyncedSessions(): List<UsageSessionEntity>

    @Query("SELECT * FROM usage_deltas WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedDeltas(): List<UsageDeltaEntity>

    @Query("UPDATE usage_sessions SET isSynced = 1, updatedAt = :updatedAt WHERE sessionId IN (:sessionIds)")
    suspend fun markSessionsSynced(
        sessionIds: List<String>,
        updatedAt: Long
    )

    @Query("UPDATE usage_deltas SET synced = 1 WHERE id IN (:deltaIds)")
    suspend fun markDeltasSynced(deltaIds: List<Long>)
}
