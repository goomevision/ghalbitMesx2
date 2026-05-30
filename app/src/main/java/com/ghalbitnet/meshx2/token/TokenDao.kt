package com.ghalbitnet.meshx2.token
import androidx.room.*

@Dao
interface TokenDao {
    @Query("SELECT SUM(amount) FROM token_transactions WHERE peerIp = :peerIp")
    fun getBalance(peerIp: String): Double?
    @Query("SELECT COUNT(*) FROM token_transactions WHERE peerIp = :peerIp AND reason = :reason")
    fun countByPeerAndReason(peerIp: String, reason: String): Int
    @Query("SELECT * FROM token_transactions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int): List<TokenTransaction>
    @Insert
    fun insertTransaction(transaction: TokenTransaction)
}
