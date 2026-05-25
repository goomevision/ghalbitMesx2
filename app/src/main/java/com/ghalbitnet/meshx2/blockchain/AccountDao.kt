package com.ghalbitnet.meshx2.blockchain

import androidx.room.*

@Dao
interface AccountDao {
    @Query("SELECT balance FROM account_state WHERE ipAddress = :ip")
    fun getBalance(ip: String): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun updateBalance(state: AccountState)
}