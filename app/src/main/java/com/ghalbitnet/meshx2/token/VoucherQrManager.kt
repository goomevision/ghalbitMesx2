package com.ghalbitnet.meshx2.token

import android.content.Context
import com.ghalbitnet.meshx2.core.server.FirebaseEconomySyncManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

object VoucherQrManager {

    private const val PREFS_NAME = "voucher_qr_manager"
    private const val KEY_VOUCHERS = "vouchers"
    const val VOUCHER_QR_PREFIX = "GHBTV:"

    data class VoucherRecord(
        val code: String,
        val issuerGlobalId: String,
        val amount: Double,
        val createdAt: Long,
        val expiresAt: Long,
        val redeemed: Boolean,
        val redeemedAt: Long,
        val redeemedByGlobalId: String,
        val revoked: Boolean,
        val revokedAt: Long
    )

    data class IssueResult(
        val success: Boolean,
        val vouchers: List<VoucherRecord>
    )

    data class RedeemResult(
        val success: Boolean,
        val alreadyUsed: Boolean,
        val amount: Double,
        val expired: Boolean,
        val revoked: Boolean
    )

    data class RevokeResult(
        val success: Boolean,
        val amount: Double
    )

    data class VoucherSummary(
        val totalCount: Int,
        val readyCount: Int,
        val usedCount: Int,
        val revokedCount: Int,
        val readyAmount: Double,
        val usedAmount: Double,
        val revokedAmount: Double
    )

    fun buildDisplaySerial(record: VoucherRecord): String {
        val datePart = android.text.format.DateFormat.format("yyyyMMdd", record.createdAt).toString()
        val amountPart =
            if (abs(record.amount - record.amount.toInt().toDouble()) < 0.0001) {
                record.amount.toInt().toString()
            } else {
                "%.2f".format(record.amount).replace(".", "")
            }
        val codePart = record.code.takeLast(6).uppercase()
        return "GHBT-$amountPart-$datePart-$codePart"
    }

    fun buildPayload(code: String): String {
        return "$VOUCHER_QR_PREFIX$code"
    }

    fun extractVoucherCode(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith(VOUCHER_QR_PREFIX)) {
            trimmed.removePrefix(VOUCHER_QR_PREFIX).trim()
        } else {
            ""
        }
    }

    suspend fun issueVouchers(
        context: Context,
        issuerGlobalId: String,
        amountPerVoucher: Double,
        quantity: Int,
        validDays: Int
    ): IssueResult {
        if (amountPerVoucher <= 0.0 || quantity <= 0 || validDays <= 0) {
            return IssueResult(false, emptyList())
        }

        TokenManager.init(context)
        TokenManager.ensureWalletBootstrap(issuerGlobalId)
        val totalAmount = amountPerVoucher * quantity
        val balance = TokenManager.getWalletBalanceForGlobalId(issuerGlobalId)
        if (balance < totalAmount) {
            return IssueResult(false, emptyList())
        }

        TokenManager.recordWalletDebit(
            issuerGlobalId,
            totalAmount,
            "VOUCHER_ISSUE:${quantity}x${"%.2f".format(amountPerVoucher)}"
        )

        val existing = load(context).toMutableList()
        val createdAt = System.currentTimeMillis()
        val expiresAt = createdAt + (validDays * 24L * 60L * 60L * 1000L)
        val vouchers =
            List(quantity) { index ->
                VoucherRecord(
                    code = UUID.randomUUID().toString().replace("-", "").uppercase(),
                    issuerGlobalId = issuerGlobalId,
                    amount = amountPerVoucher,
                    createdAt = createdAt + index,
                    expiresAt = expiresAt,
                    redeemed = false,
                    redeemedAt = 0L,
                    redeemedByGlobalId = "",
                    revoked = false,
                    revokedAt = 0L
                )
            }
        existing.addAll(vouchers)
        save(context, existing)
        FirebaseEconomySyncManager.enqueueVoucherIssue(context, issuerGlobalId, vouchers)
        return IssueResult(true, vouchers)
    }

    suspend fun redeemVoucher(
        context: Context,
        voucherCode: String,
        redeemerGlobalId: String
    ): RedeemResult {
        val normalizedCode = voucherCode.trim().uppercase()
        if (normalizedCode.isBlank()) {
            return RedeemResult(false, false, 0.0, false, false)
        }

        TokenManager.init(context)
        TokenManager.ensureWalletBootstrap(redeemerGlobalId)

        val records = load(context).toMutableList()
        val index = records.indexOfFirst { it.code == normalizedCode }
        if (index < 0) {
            return RedeemResult(false, false, 0.0, false, false)
        }

        val record = records[index]
        if (record.redeemed) {
            return RedeemResult(false, true, record.amount, false, false)
        }
        if (record.revoked) {
            return RedeemResult(false, false, record.amount, false, true)
        }
        if (System.currentTimeMillis() > record.expiresAt) {
            return RedeemResult(false, false, record.amount, true, false)
        }

        TokenManager.recordWalletCredit(
            redeemerGlobalId,
            record.amount,
            "VOUCHER_REDEEM:${record.code}"
        )

        records[index] =
            record.copy(
                redeemed = true,
                redeemedAt = System.currentTimeMillis(),
                redeemedByGlobalId = redeemerGlobalId,
                revoked = false,
                revokedAt = 0L
            )
        save(context, records)
        FirebaseEconomySyncManager.enqueueVoucherRedeem(context, records[index])
        return RedeemResult(true, false, record.amount, false, false)
    }

    suspend fun revokeVoucher(
        context: Context,
        issuerGlobalId: String,
        voucherCode: String
    ): RevokeResult {
        val normalizedCode = voucherCode.trim().uppercase()
        if (normalizedCode.isBlank()) {
            return RevokeResult(false, 0.0)
        }

        TokenManager.init(context)
        TokenManager.ensureWalletBootstrap(issuerGlobalId)
        val records = load(context).toMutableList()
        val index =
            records.indexOfFirst {
                it.code == normalizedCode && it.issuerGlobalId == issuerGlobalId
            }
        if (index < 0) {
            return RevokeResult(false, 0.0)
        }

        val record = records[index]
        if (record.redeemed || record.revoked) {
            return RevokeResult(false, record.amount)
        }

        TokenManager.recordWalletCredit(
            issuerGlobalId,
            record.amount,
            "VOUCHER_REVOKE:${record.code}"
        )
        records[index] =
            record.copy(
                revoked = true,
                revokedAt = System.currentTimeMillis()
            )
        save(context, records)
        FirebaseEconomySyncManager.enqueueVoucherRevoke(context, records[index])
        return RevokeResult(true, record.amount)
    }

    fun vouchersForIssuer(
        context: Context,
        issuerGlobalId: String
    ): List<VoucherRecord> {
        return load(context)
            .filter { it.issuerGlobalId == issuerGlobalId }
            .sortedByDescending { it.createdAt }
    }

    fun summaryForIssuer(
        context: Context,
        issuerGlobalId: String
    ): VoucherSummary {
        val vouchers = vouchersForIssuer(context, issuerGlobalId)
        val ready = vouchers.filter { !it.redeemed && !it.revoked && System.currentTimeMillis() <= it.expiresAt }
        val used = vouchers.filter { it.redeemed }
        val revoked = vouchers.filter { it.revoked || (!it.redeemed && System.currentTimeMillis() > it.expiresAt) }
        return VoucherSummary(
            totalCount = vouchers.size,
            readyCount = ready.size,
            usedCount = used.size,
            revokedCount = revoked.size,
            readyAmount = ready.sumOf { it.amount },
            usedAmount = used.sumOf { it.amount },
            revokedAmount = revoked.sumOf { it.amount }
        )
    }

    private fun load(context: Context): List<VoucherRecord> {
        val raw =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_VOUCHERS, "[]")
                .orEmpty()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        VoucherRecord(
                            code = item.optString("code"),
                            issuerGlobalId = item.optString("issuerGlobalId"),
                            amount = item.optDouble("amount"),
                            createdAt = item.optLong("createdAt"),
                            expiresAt = item.optLong("expiresAt"),
                            redeemed = item.optBoolean("redeemed"),
                            redeemedAt = item.optLong("redeemedAt"),
                            redeemedByGlobalId = item.optString("redeemedByGlobalId"),
                            revoked = item.optBoolean("revoked"),
                            revokedAt = item.optLong("revokedAt")
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun save(
        context: Context,
        records: List<VoucherRecord>
    ) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject().apply {
                    put("code", record.code)
                    put("issuerGlobalId", record.issuerGlobalId)
                    put("amount", record.amount)
                    put("createdAt", record.createdAt)
                    put("expiresAt", record.expiresAt)
                    put("redeemed", record.redeemed)
                    put("redeemedAt", record.redeemedAt)
                    put("redeemedByGlobalId", record.redeemedByGlobalId)
                    put("revoked", record.revoked)
                    put("revokedAt", record.revokedAt)
                }
            )
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_VOUCHERS, array.toString())
            .apply()
    }
}
