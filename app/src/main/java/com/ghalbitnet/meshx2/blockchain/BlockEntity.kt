package com.ghalbitnet.meshx2.blockchain

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocks")
data class BlockEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val blockNumber: Long,

    val hash: String,

    val previousHash: String,

    val timestamp: Long,

    val data: String,

    val nonce: Long = 0,

    val minerAddress: String = "",

    val reward: Double = 0.0
)
