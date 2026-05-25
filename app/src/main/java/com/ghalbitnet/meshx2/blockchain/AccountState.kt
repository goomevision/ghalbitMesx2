package com.ghalbitnet.meshx2.blockchain

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "account_state")
data class AccountState(
    @PrimaryKey val ipAddress: String,
    var balance: Double
)