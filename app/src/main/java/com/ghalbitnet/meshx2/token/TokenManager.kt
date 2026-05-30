package com.ghalbitnet.meshx2.token
import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.routing.MeshRegistry
import com.ghalbitnet.meshx2.wallet.UnifiedWalletRegistry
import kotlinx.coroutines.*

object TokenManager {
    private const val TAG = "GHALBIT-WALLET"
    private const val GENESIS_REASON = "GENESIS_AIRDROP"
    private const val BUILDER_WALLET_KEY = "wallet:BUILDER_FOUNDATION"
    private lateinit var db: TokenDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var initialized = false

    fun init(context: Context) {
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
            db.tokenDao().insertTransaction(
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

    suspend fun getBuilderWalletBalance(): Double {
        if (!initialized) return 0.0
        return db.tokenDao().getBalance(BUILDER_WALLET_KEY) ?: 0.0
    }

    suspend fun getRecentTransactions(limit: Int): List<TokenTransaction> {
        if (!initialized) return emptyList()
        return db.tokenDao().getRecentTransactions(limit)
    }

    suspend fun recordWalletCredit(globalId: String, amount: Double, reason: String) {
        if (!initialized || amount == 0.0) return
        db.tokenDao().insertTransaction(
            TokenTransaction(
                peerIp = walletKey(globalId),
                peerName = globalId,
                amount = amount,
                reason = reason
            )
        )
    }

    suspend fun recordNodeOwnerReward(
        context: Context,
        nodeId: String,
        nodeLabel: String,
        amount: Double,
        reason: String = "MULTI_NODE_REWARD"
    ) {
        if (!initialized || amount == 0.0) return
        val binding = UnifiedWalletRegistry.getBinding(context, nodeId) ?: return
        if (!binding.rewardCollectionEnabled) {
            Log.d(TAG, "Skipped owner reward for $nodeId because collection is disabled")
            return
        }

        recordWalletCredit(
            globalId = binding.ownerWalletId,
            amount = amount,
            reason = "$reason:$nodeId"
        )
        recordPeerReward(
            peerIp = "owned-node:$nodeId",
            peerName = nodeLabel,
            amount = amount,
            reason = "$reason:LOCAL_NODE"
        )
        Log.d(TAG, "Recorded owner reward for wallet ${binding.ownerWalletId} from node $nodeId")
    }

    suspend fun getOwnerWalletBalance(
        context: Context,
        ownerWalletId: String
    ): Double {
        if (!initialized) return 0.0
        val directWalletBalance = getLocalWalletBalance(ownerWalletId)
        val ownedNodes = UnifiedWalletRegistry.getOwnedNodes(context, ownerWalletId)
        val nodeBalances = ownedNodes.sumOf { node ->
            db.tokenDao().getBalance("owned-node:${node.nodeId}") ?: 0.0
        }
        return directWalletBalance + nodeBalances
    }

    suspend fun recordWalletDebit(globalId: String, amount: Double, reason: String) {
        if (!initialized || amount == 0.0) return
        recordWalletCredit(globalId, -kotlin.math.abs(amount), reason)
    }

    suspend fun recordTreasury(amount: Double, reason: String) {
        if (!initialized || amount == 0.0) return
        db.tokenDao().insertTransaction(
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
        db.tokenDao().insertTransaction(
            TokenTransaction(
                peerIp = BUILDER_WALLET_KEY,
                peerName = "BUILDER_FOUNDATION",
                amount = amount,
                reason = reason
            )
        )
    }

    suspend fun recordPeerReward(peerIp: String, peerName: String, amount: Double, reason: String) {
        if (!initialized || amount == 0.0) return
        db.tokenDao().insertTransaction(
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
        // TODO unified identity:
        // wallet storage already leans on globalId, but peer reward records
        // still mix peerIp and peerName in legacy transaction rows.
        return "wallet:$globalId"
    }
}
