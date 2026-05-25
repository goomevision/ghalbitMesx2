package com.ghalbitnet.meshx2.token
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "token_transactions")
data class TokenTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val peerIp: String, val peerName: String,
    val amount: Double, val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)