package com.ghalbitnet.meshx2.core.server

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.ghalbitnet.meshx2.token.TokenTransaction
import com.ghalbitnet.meshx2.token.VoucherQrManager
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

object FirebaseEconomySyncManager {

    private const val PREFS_NAME = "firebase_economy_sync"
    private const val KEY_QUEUE = "queue"
    private const val KEY_STATUS_MAP = "status_map"

    private enum class OperationType {
        LEDGER_EVENT,
        VOUCHER_ISSUE,
        VOUCHER_REDEEM,
        VOUCHER_REVOKE
    }

    enum class SyncState {
        PENDING,
        SYNCED,
        CONFLICT,
        FAILED
    }

    data class SyncSummary(
        val pendingCount: Int,
        val syncedCount: Int,
        val conflictCount: Int,
        val failedCount: Int
    )

    data class SyncStatus(
        val opId: String,
        val type: String,
        val state: SyncState,
        val updatedAt: Long,
        val detail: String
    )

    private data class QueueItem(
        val opId: String,
        val type: OperationType,
        val payload: String,
        val createdAt: Long
    )

    private data class SyncOutcome(
        val state: SyncState,
        val detail: String = ""
    )

    @Volatile
    private var syncing = false

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun enqueueLedgerEvent(
        context: Context,
        transaction: TokenTransaction
    ) {
        enqueue(
            context,
            QueueItem(
                opId = "ledger-${transaction.id}",
                type = OperationType.LEDGER_EVENT,
                payload =
                    JSONObject()
                        .put("id", transaction.id)
                        .put("peerIp", transaction.peerIp)
                        .put("peerName", transaction.peerName)
                        .put("amount", transaction.amount)
                        .put("reason", transaction.reason)
                        .put("timestamp", transaction.timestamp)
                        .toString(),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun enqueueVoucherIssue(
        context: Context,
        issuerGlobalId: String,
        vouchers: List<VoucherQrManager.VoucherRecord>
    ) {
        val voucherArray = JSONArray()
        vouchers.forEach { voucher ->
            voucherArray.put(voucherToJson(voucher))
        }
        enqueue(
            context,
            QueueItem(
                opId = "voucher-issue-${issuerGlobalId}-${vouchers.firstOrNull()?.code.orEmpty()}",
                type = OperationType.VOUCHER_ISSUE,
                payload =
                    JSONObject()
                        .put("issuerGlobalId", issuerGlobalId)
                        .put("vouchers", voucherArray)
                        .toString(),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun enqueueVoucherRedeem(
        context: Context,
        voucher: VoucherQrManager.VoucherRecord
    ) {
        enqueue(
            context,
            QueueItem(
                opId = "voucher-redeem-${voucher.code}",
                type = OperationType.VOUCHER_REDEEM,
                payload = voucherToJson(voucher).toString(),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun enqueueVoucherRevoke(
        context: Context,
        voucher: VoucherQrManager.VoucherRecord
    ) {
        enqueue(
            context,
            QueueItem(
                opId = "voucher-revoke-${voucher.code}",
                type = OperationType.VOUCHER_REVOKE,
                payload = voucherToJson(voucher).toString(),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun syncPending(context: Context): Int {
        if (syncing || !FirebaseBootstrap.ensureInitialized(context)) {
            return pendingCount(context)
        }
        syncing = true
        return try {
            val firestore = FirebaseFirestore.getInstance()
            val queue = loadQueue(context)
            val remaining = mutableListOf<QueueItem>()
            queue.forEach { item ->
                val outcome =
                    runCatching {
                        when (item.type) {
                            OperationType.LEDGER_EVENT -> syncLedgerEvent(firestore, item)
                            OperationType.VOUCHER_ISSUE -> syncVoucherIssue(firestore, item)
                            OperationType.VOUCHER_REDEEM -> syncVoucherRedeem(firestore, item)
                            OperationType.VOUCHER_REVOKE -> syncVoucherRevoke(firestore, item)
                        }
                    }.getOrElse {
                        SyncOutcome(
                            state = SyncState.FAILED,
                            detail = it.message ?: "Sinkron gagal."
                        )
                    }

                updateStatus(context, item, outcome)
                if (outcome.state == SyncState.PENDING || outcome.state == SyncState.FAILED) {
                    remaining += item
                }
            }
            saveQueue(context, remaining)
            remaining.size
        } finally {
            syncing = false
        }
    }

    fun pendingCount(context: Context): Int = loadQueue(context).size

    fun summary(context: Context): SyncSummary {
        val all = loadStatuses(context)
        return SyncSummary(
            pendingCount = all.count { it.state == SyncState.PENDING },
            syncedCount = all.count { it.state == SyncState.SYNCED },
            conflictCount = all.count { it.state == SyncState.CONFLICT },
            failedCount = all.count { it.state == SyncState.FAILED }
        )
    }

    fun statusForVoucherCode(
        context: Context,
        code: String
    ): SyncStatus? {
        val all = loadStatuses(context)
        return all
            .filter { it.opId.endsWith(code) || it.detail.contains(code, ignoreCase = true) }
            .maxByOrNull { it.updatedAt }
    }

    fun recentStatuses(
        context: Context,
        limit: Int
    ): List<SyncStatus> {
        return loadStatuses(context)
            .sortedByDescending { it.updatedAt }
            .take(limit)
    }

    private suspend fun syncLedgerEvent(
        firestore: FirebaseFirestore,
        item: QueueItem
    ): SyncOutcome {
        val payload = JSONObject(item.payload)
        val peerIp = payload.optString("peerIp")
        val peerName = payload.optString("peerName")
        val amount = payload.optDouble("amount")
        val reason = payload.optString("reason")
        val timestamp = payload.optLong("timestamp")

        if (peerIp.startsWith("wallet:")) {
            val walletGlobalId = peerIp.removePrefix("wallet:")
            val txRef = firestore.collection("walletTransactions").document(item.opId)
            val walletRef = firestore.collection("wallets").document(walletGlobalId)

            return firestore.runTransaction { transaction ->
                val txSnap = transaction.get(txRef)
                if (txSnap.exists()) {
                    return@runTransaction existingWalletTransactionOutcome(
                        txSnap,
                        amount = amount,
                        reason = reason,
                        walletGlobalId = walletGlobalId
                    )
                }
                val walletSnap = transaction.get(walletRef)
                val currentBalance = walletSnap.getDouble("balance") ?: 0.0
                val nextBalance = currentBalance + amount
                transaction.set(
                    walletRef,
                    mapOf(
                        "globalId" to walletGlobalId,
                        "balance" to nextBalance,
                        "updatedAt" to Timestamp.now(),
                        "lastReason" to reason,
                        "lastDirection" to if (amount >= 0.0) "CREDIT" else "DEBIT",
                        "lastAmount" to kotlin.math.abs(amount),
                        "lastController" to "SYSTEM",
                        "ownerClass" to classifyWalletOwner(walletGlobalId),
                        "source" to "android-offline-sync"
                    ),
                    SetOptions.merge()
                )
                transaction.set(
                    txRef,
                    mapOf(
                        "walletGlobalId" to walletGlobalId,
                        "amount" to kotlin.math.abs(amount),
                        "rawAmount" to amount,
                        "direction" to if (amount >= 0.0) "CREDIT" else "DEBIT",
                        "reason" to reason,
                        "controller" to "SYSTEM",
                        "ownerClass" to classifyWalletOwner(walletGlobalId),
                        "createdAtClient" to timestamp,
                        "createdAt" to Timestamp.now(),
                        "balanceBefore" to currentBalance,
                        "balanceAfter" to nextBalance,
                        "source" to "android-offline-sync"
                    )
                )
                SyncOutcome(SyncState.SYNCED, "Wallet $walletGlobalId tersinkron.")
            }.await()
        }

        val eventRef = firestore.collection("economyEvents").document(item.opId)
        val existing = eventRef.get().await()
        if (existing.exists()) {
            val matches =
                existing.getString("peerIp") == peerIp &&
                    (existing.getDouble("amount") ?: 0.0) == amount &&
                    existing.getString("reason") == reason
            return if (matches) {
                SyncOutcome(SyncState.SYNCED, "Event $peerName sudah ada.")
            } else {
                SyncOutcome(SyncState.CONFLICT, "Event $peerName bentrok dengan data server.")
            }
        }
        eventRef.set(
            mapOf(
                "peerIp" to peerIp,
                "peerName" to peerName,
                "amount" to amount,
                "reason" to reason,
                "createdAtClient" to timestamp,
                "createdAt" to Timestamp.now(),
                "source" to "android-offline-sync"
            ),
            SetOptions.merge()
        ).await()
        return SyncOutcome(SyncState.SYNCED, "Event $peerName tersinkron.")
    }

    private suspend fun syncVoucherIssue(
        firestore: FirebaseFirestore,
        item: QueueItem
    ): SyncOutcome {
        val root = JSONObject(item.payload)
        val vouchers = root.optJSONArray("vouchers") ?: JSONArray()
        var synced = 0
        var conflict = 0
        for (i in 0 until vouchers.length()) {
            val voucher = vouchers.optJSONObject(i) ?: continue
            val code = voucher.optString("code")
            if (code.isBlank()) continue
            val ref = firestore.collection("vouchers").document(code)
            val outcome =
                firestore.runTransaction { transaction ->
                    val snap = transaction.get(ref)
                    if (snap.exists()) {
                        val same =
                            snap.getString("issuerGlobalId") == voucher.optString("issuerGlobalId") &&
                                (snap.getDouble("amount") ?: 0.0) == voucher.optDouble("amount")
                        return@runTransaction if (same) {
                            SyncOutcome(SyncState.SYNCED, "Voucher $code sudah ada.")
                        } else {
                            SyncOutcome(SyncState.CONFLICT, "Voucher $code bentrok.")
                        }
                    }
                    transaction.set(
                        ref,
                        mapOf(
                            "code" to code,
                            "issuerGlobalId" to voucher.optString("issuerGlobalId"),
                            "amount" to voucher.optDouble("amount"),
                            "createdAtClient" to voucher.optLong("createdAt"),
                            "expiresAtClient" to voucher.optLong("expiresAt"),
                            "redeemed" to voucher.optBoolean("redeemed"),
                            "redeemedAtClient" to voucher.optLong("redeemedAt"),
                            "redeemedByGlobalId" to voucher.optString("redeemedByGlobalId"),
                            "revoked" to voucher.optBoolean("revoked"),
                            "revokedAtClient" to voucher.optLong("revokedAt"),
                            "createdAt" to Timestamp.now(),
                            "source" to "android-offline-sync"
                        )
                    )
                    SyncOutcome(SyncState.SYNCED, "Voucher $code tersinkron.")
                }.await()
            if (outcome.state == SyncState.CONFLICT) conflict += 1 else synced += 1
        }
        return if (conflict > 0) {
            SyncOutcome(SyncState.CONFLICT, "$conflict voucher bentrok saat issue.")
        } else {
            SyncOutcome(SyncState.SYNCED, "$synced voucher issue tersinkron.")
        }
    }

    private suspend fun syncVoucherRedeem(
        firestore: FirebaseFirestore,
        item: QueueItem
    ): SyncOutcome {
        val voucher = JSONObject(item.payload)
        val code = voucher.optString("code")
        if (code.isBlank()) return SyncOutcome(SyncState.FAILED, "Kode voucher kosong.")
        val ref = firestore.collection("vouchers").document(code)
        return firestore.runTransaction { transaction ->
            val snap = transaction.get(ref)
            if (snap.exists()) {
                if (snap.getBoolean("revoked") == true) {
                    return@runTransaction SyncOutcome(SyncState.CONFLICT, "Voucher $code sudah ditarik di server.")
                }
                if (snap.getBoolean("redeemed") == true) {
                    return@runTransaction if (
                        snap.getString("redeemedByGlobalId") == voucher.optString("redeemedByGlobalId")
                    ) {
                        SyncOutcome(SyncState.SYNCED, "Voucher $code sudah diredeem server.")
                    } else {
                        SyncOutcome(SyncState.CONFLICT, "Voucher $code sudah dipakai pihak lain.")
                    }
                }
            }
            transaction.set(
                ref,
                mapOf(
                    "redeemed" to true,
                    "redeemedAtClient" to voucher.optLong("redeemedAt"),
                    "redeemedByGlobalId" to voucher.optString("redeemedByGlobalId"),
                    "updatedAt" to Timestamp.now(),
                    "source" to "android-offline-sync"
                ),
                SetOptions.merge()
            )
            SyncOutcome(SyncState.SYNCED, "Voucher $code redeem tersinkron.")
        }.await()
    }

    private suspend fun syncVoucherRevoke(
        firestore: FirebaseFirestore,
        item: QueueItem
    ): SyncOutcome {
        val voucher = JSONObject(item.payload)
        val code = voucher.optString("code")
        if (code.isBlank()) return SyncOutcome(SyncState.FAILED, "Kode voucher kosong.")
        val ref = firestore.collection("vouchers").document(code)
        return firestore.runTransaction { transaction ->
            val snap = transaction.get(ref)
            if (snap.exists()) {
                if (snap.getBoolean("redeemed") == true) {
                    return@runTransaction SyncOutcome(SyncState.CONFLICT, "Voucher $code sudah diredeem, tidak bisa ditarik.")
                }
                if (snap.getBoolean("revoked") == true) {
                    return@runTransaction SyncOutcome(SyncState.SYNCED, "Voucher $code sudah direvoke server.")
                }
            }
            transaction.set(
                ref,
                mapOf(
                    "revoked" to true,
                    "revokedAtClient" to voucher.optLong("revokedAt"),
                    "updatedAt" to Timestamp.now(),
                    "source" to "android-offline-sync"
                ),
                SetOptions.merge()
            )
            SyncOutcome(SyncState.SYNCED, "Voucher $code revoke tersinkron.")
        }.await()
    }

    private fun existingWalletTransactionOutcome(
        txSnap: DocumentSnapshot,
        amount: Double,
        reason: String,
        walletGlobalId: String
    ): SyncOutcome {
        val same =
            (txSnap.getDouble("rawAmount") ?: txSnap.getDouble("amount") ?: 0.0) == amount &&
                txSnap.getString("reason") == reason &&
                txSnap.getString("walletGlobalId") == walletGlobalId
        return if (same) {
            SyncOutcome(SyncState.SYNCED, "Wallet $walletGlobalId sudah tercatat di server.")
        } else {
            SyncOutcome(SyncState.CONFLICT, "Wallet $walletGlobalId bentrok dengan transaksi server.")
        }
    }

    private fun voucherToJson(record: VoucherQrManager.VoucherRecord): JSONObject {
        return JSONObject()
            .put("code", record.code)
            .put("issuerGlobalId", record.issuerGlobalId)
            .put("amount", record.amount)
            .put("createdAt", record.createdAt)
            .put("expiresAt", record.expiresAt)
            .put("redeemed", record.redeemed)
            .put("redeemedAt", record.redeemedAt)
            .put("redeemedByGlobalId", record.redeemedByGlobalId)
            .put("revoked", record.revoked)
            .put("revokedAt", record.revokedAt)
    }

    private fun classifyWalletOwner(globalId: String): String {
        return if (globalId == "BUILDER_FOUNDATION") "BUILDER" else "USER"
    }

    private fun enqueue(
        context: Context,
        item: QueueItem
    ) {
        val queue = loadQueue(context).toMutableList()
        if (queue.none { it.opId == item.opId }) {
            queue += item
            saveQueue(context, queue)
            saveStatus(
                context,
                SyncStatus(
                    opId = item.opId,
                    type = item.type.name,
                    state = SyncState.PENDING,
                    updatedAt = System.currentTimeMillis(),
                    detail = "Menunggu internet/Firebase."
                )
            )
        }
    }

    private fun updateStatus(
        context: Context,
        item: QueueItem,
        outcome: SyncOutcome
    ) {
        saveStatus(
            context,
            SyncStatus(
                opId = item.opId,
                type = item.type.name,
                state = outcome.state,
                updatedAt = System.currentTimeMillis(),
                detail = outcome.detail.ifBlank {
                    when (outcome.state) {
                        SyncState.PENDING -> "Masih menunggu sinkron."
                        SyncState.SYNCED -> "Sudah sinkron."
                        SyncState.CONFLICT -> "Bentrok dengan server."
                        SyncState.FAILED -> "Gagal sinkron."
                    }
                }
            )
        )
    }

    private fun loadQueue(context: Context): List<QueueItem> {
        val raw = prefs(context).getString(KEY_QUEUE, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        QueueItem(
                            opId = item.optString("opId"),
                            type = OperationType.valueOf(item.optString("type")),
                            payload = item.optString("payload"),
                            createdAt = item.optLong("createdAt")
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun saveQueue(
        context: Context,
        items: List<QueueItem>
    ) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("opId", item.opId)
                    .put("type", item.type.name)
                    .put("payload", item.payload)
                    .put("createdAt", item.createdAt)
            )
        }
        prefs(context).edit().putString(KEY_QUEUE, array.toString()).apply()
    }

    private fun loadStatuses(context: Context): List<SyncStatus> {
        val raw = prefs(context).getString(KEY_STATUS_MAP, "{}").orEmpty()
        return runCatching {
            val root = JSONObject(raw)
            buildList {
                root.keys().forEach { key ->
                    val item = root.optJSONObject(key) ?: return@forEach
                    add(
                        SyncStatus(
                            opId = key,
                            type = item.optString("type"),
                            state = runCatching { SyncState.valueOf(item.optString("state")) }
                                .getOrDefault(SyncState.PENDING),
                            updatedAt = item.optLong("updatedAt"),
                            detail = item.optString("detail")
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun saveStatus(
        context: Context,
        status: SyncStatus
    ) {
        val root = JSONObject(prefs(context).getString(KEY_STATUS_MAP, "{}").orEmpty())
        root.put(
            status.opId,
            JSONObject()
                .put("type", status.type)
                .put("state", status.state.name)
                .put("updatedAt", status.updatedAt)
                .put("detail", status.detail)
        )
        prefs(context).edit().putString(KEY_STATUS_MAP, root.toString()).apply()
    }
}
