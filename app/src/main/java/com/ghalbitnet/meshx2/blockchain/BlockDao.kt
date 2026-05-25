package com.ghalbitnet.meshx2.blockchain

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * =========================================================
 * BLOCK DAO
 * =========================================================
 *
 * Akses database blockchain.
 *
 * =========================================================
 * FUTURE ROADMAP
 * =========================================================
 *
 * // TODO FUTURE:
 *
 * Tambahkan:
 *
 * - query by hash
 * - pruning
 * - compressed sync
 * - validator query
 * - route reputation
 *
 * =========================================================
 */

@Dao
interface BlockDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(
        block: BlockEntity
    )

    @Query(
        "SELECT * FROM blocks ORDER BY blockNumber ASC"
    )
    suspend fun getAll(): List<BlockEntity>

    @Query(
        "SELECT * FROM blocks ORDER BY blockNumber DESC LIMIT 1"
    )
    suspend fun getLatest(): BlockEntity?
}
