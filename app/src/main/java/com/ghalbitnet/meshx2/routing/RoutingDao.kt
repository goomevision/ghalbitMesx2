package com.ghalbitnet.meshx2.routing
import androidx.room.*

@Dao
interface RoutingDao {
    @Query("SELECT * FROM routing_table WHERE destinationIp = :destIp ORDER BY hopCount ASC")
    fun getRoutes(destIp: String): List<RoutingTableEntry>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertEntry(entry: RoutingTableEntry)
    @Query("DELETE FROM routing_table WHERE lastUpdated < :threshold")
    fun deleteOlderThan(threshold: Long)
}