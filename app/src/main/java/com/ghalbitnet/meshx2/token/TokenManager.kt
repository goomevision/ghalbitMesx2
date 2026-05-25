package com.ghalbitnet.meshx2.token
import android.content.Context
import com.ghalbitnet.meshx2.core.server.FirebaseEconomySyncManager
import com.ghalbitnet.meshx2.routing.MeshRegistry
import kotlinx.coroutines.*

object TokenManager {
    private const val GENESIS_REASON = "GENESIS_AIRDROP"
    private const val BUILDER_WALLET_KEY = "wallet:BUILDER_FOUNDATION"
    private const val VALIDATOR_POOL_KEY = "VALIDATOR_POOL"
    private lateinit var db: TokenDatabase
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var initialized = false

    fun init(context: Context) {
        appContext = context.applicationContext
        if (!initialized) { db = TokenDatabase.getInstance(context); initialized = true }
    }

    fun recordReward(peerIp: String, peerName: String, amount: Double, reason: String = "RELAY_REWARD") {
        if (!initialized) return
        scope.launch {
            recordPeerReward(peerIp, peerName, amount, reason)
        }
    }

    suspend fun ensureWalletBootstrap(globalId: String) {
        if (!initialized) return
        val walletKey = walletKey(globalId)
        if (db.tokenDao().countByPeerAndReason(walletKey, GENESIS_REASON) == 0) {
            insertTransactionAndQueue(
                TokenTransaction(
                    peerIp = walletKey,
                    peerName = globalId,
                    amount = 250.0,
                    reason = GENESIS_REASON
                )
            )
        }
    }

    suspend fun getLocalWalletBalance(globalId: String): Double {
        if (!initialized) return 0.0
        return db.tokenDao().getBalance(walletKey(globalId)) ?: 0.0
    }

    suspend fun getWalletBalanceForGlobalId(globalId: String): Double {
        if (!initialized) return 0.0
        return db.tokenDao().getBalance(walletKey(globalId)) ?: 0.0
    }

    suspend fun getBuilderWalletBalance(): Double {
        if (!initialized) return 0.0
        return db.tokenDao().getBalance(BUILDER_WALLET_KEY) ?: 0.0
    }

    suspend fun getRecentTransactions(limit: Int): List<TokenTransaction> {
        if (!initialized) return emptyList()
        return db.tokenDao().getRecentTransactions(limit)
    }

    suspend fun getWalletTransactions(globalId: String, limit: Int): List<TokenTransaction> {
        if (!initialized) return emptyList()
        return db.tokenDao().getTransactionsForWallet(walletKey(globalId), limit)
    }

    suspend fun recordWalletCredit(globalId: String, amount: Double, reason: String) {
        if (!initialized || amount == 0.0) return
        insertTransactionAndQueue(
            TokenTransaction(
                peerIp = walletKey(globalId),
                peerName = globalId,
                amount = amount,
                reason = reason
            )
        )
    }

    suspend fun recordWalletDebit(globalId: String, amount: Double, reason: String) {
        if (!initialized || amount == 0.0) return
        recordWalletCredit(globalId, -kotlin.math.abs(amount), reason)
    }

    suspend fun transferBetweenWallets(
        fromGlobalId: String,
        toGlobalId: String,
        amount: Double,
        reason: String
    ): Boolean {
        if (!initialized || amount <= 0.0) return false
        ensureWalletBootstrap(fromGlobalId)
        ensureWalletBootstrap(toGlobalId)
        val balance = getWalletBalanceForGlobalId(fromGlobalId)
        if (balance < amount) {
            return false
        }
        recordWalletDebit(fromGlobalId, amount, "TRANSFER_OUT:$reason")
        recordWalletCredit(toGlobalId, amount, "TRANSFER_IN:$reason")
        return true
    }

    suspend fun recordTreasury(amount: Double, reason: String) {
        if (!initialized || amount == 0.0) return
        insertTransactionAndQueue(
            TokenTransaction(
                peerIp = "TREASURY_POOL",
                peerName = "TREASURY_POOL",
                amount = amount,
                reason = reason
            )
        )
    }

    suspend fun recordBuilderReward(amount: Double, reason: String) {
        if (!initialized || amount == 0.0) return
        insertTransactionAndQueue(
            TokenTransaction(
                peerIp = BUILDER_WALLET_KEY,
                peerName = "BUILDER_FOUNDATION",
                amount = amount,
                reason = reason
            )
        )
    }

    suspend fun recordValidatorReward(amount: Double, reason: String) {
        if (!initialized || amount == 0.0) return
        insertTransactionAndQueue(
            TokenTransaction(
                peerIp = VALIDATOR_POOL_KEY,
                peerName = "VALIDATOR_POOL",
                amount = amount,
                reason = reason
            )
        )
    }

    suspend fun recordPeerReward(peerIp: String, peerName: String, amount: Double, reason: String) {
        if (!initialized || amount == 0.0) return
        insertTransactionAndQueue(
            TokenTransaction(
                peerIp = peerIp,
                peerName = peerName,
                amount = amount,
                reason = reason
            )
        )

        MeshRegistry.getNode(peerIp)?.let { node ->
            MeshRegistry.updateNode(node.copy(balance = node.balance + amount))
        }
    }

    private fun walletKey(globalId: String): String {
        return "wallet:$globalId"
    }

    private suspend fun insertTransactionAndQueue(transaction: TokenTransaction): Long {
        val id = db.tokenDao().insertTransaction(transaction)
        FirebaseEconomySyncManager.enqueueLedgerEvent(appContext, transaction.copy(id = id))
        return id
    }
}
