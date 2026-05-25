package com.ghalbitnet.meshx2.routing
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routing_table")
data class RoutingTableEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val destinationIp: String, val nextHopIp: String,
    val hopCount: Int, val latencyMs: Long,
    val trustScore: Int, val lastUpdated: Long = System.currentTimeMillis()
)