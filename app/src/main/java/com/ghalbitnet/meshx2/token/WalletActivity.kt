package com.ghalbitnet.meshx2.token

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.chat.GlobalContactDirectory
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.core.server.FirebaseEconomySyncManager
import com.ghalbitnet.meshx2.core.server.FirebaseRemoteSyncManager
import com.ghalbitnet.meshx2.economy.EconomyRoleManager
import com.ghalbitnet.meshx2.economy.InternetBridgePolicyManager
import com.ghalbitnet.meshx2.security.KeyStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WalletActivity : AppCompatActivity() {

    companion object {
        private const val WALLET_QR_PREFIX = "GHBT:"
    }

    private enum class WalletFilter {
        ALL,
        INCOMING,
        OUTGOING,
        REWARD
    }

    private lateinit var keyStore: KeyStoreManager
    private lateinit var globalId: String
    private lateinit var txtWalletIdentity: TextView
    private lateinit var txtWalletBalance: TextView
    private lateinit var txtWalletInternetStatus: TextView
    private lateinit var txtWalletVoucherSummary: TextView
    private lateinit var txtWalletRecent: TextView
    private lateinit var btnWalletTransfer: Button
    private lateinit var btnWalletIssueVoucher: Button
    private lateinit var btnWalletViewVouchers: Button
    private lateinit var btnWalletShowQr: Button
    private lateinit var btnWalletScanQr: Button
    private lateinit var btnWalletRefresh: Button
    private lateinit var btnWalletFilterAll: Button
    private lateinit var btnWalletFilterIncoming: Button
    private lateinit var btnWalletFilterOutgoing: Button
    private lateinit var btnWalletFilterReward: Button
    private var activeFilter: WalletFilter = WalletFilter.ALL

    private val qrScannerLauncher =
        registerForActivityResult(ScanContract()) { result ->
            val voucherCode =
                VoucherQrManager.extractVoucherCode(result.contents.orEmpty())
            if (voucherCode.isNotBlank()) {
                redeemVoucher(voucherCode)
                return@registerForActivityResult
            }
            val scannedGlobalId =
                parseWalletQr(result.contents.orEmpty())
            if (scannedGlobalId.isBlank()) {
                if (!result.contents.isNullOrBlank()) {
                    Toast.makeText(
                        this,
                        getString(R.string.wallet_scan_invalid),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@registerForActivityResult
            }
            Toast.makeText(
                this,
                getString(R.string.wallet_scan_success_target, scannedGlobalId),
                Toast.LENGTH_SHORT
            ).show()
            showTransferDialog(scannedGlobalId)
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startQrScanner()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.wallet_scan_camera_denied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)

        keyStore = KeyStoreManager(this)
        globalId = GlobalMeshIdentityManager.buildGlobalId(keyStore.publicKeyBase64)
        TokenManager.init(this)

        txtWalletIdentity = findViewById(R.id.txtWalletIdentity)
        txtWalletBalance = findViewById(R.id.txtWalletBalance)
        txtWalletInternetStatus = findViewById(R.id.txtWalletInternetStatus)
        txtWalletVoucherSummary = findViewById(R.id.txtWalletVoucherSummary)
        txtWalletRecent = findViewById(R.id.txtWalletRecent)
        btnWalletTransfer = findViewById(R.id.btnWalletTransfer)
        btnWalletIssueVoucher = findViewById(R.id.btnWalletIssueVoucher)
        btnWalletViewVouchers = findViewById(R.id.btnWalletViewVouchers)
        btnWalletShowQr = findViewById(R.id.btnWalletShowQr)
        btnWalletScanQr = findViewById(R.id.btnWalletScanQr)
        btnWalletRefresh = findViewById(R.id.btnWalletRefresh)
        btnWalletFilterAll = findViewById(R.id.btnWalletFilterAll)
        btnWalletFilterIncoming = findViewById(R.id.btnWalletFilterIncoming)
        btnWalletFilterOutgoing = findViewById(R.id.btnWalletFilterOutgoing)
        btnWalletFilterReward = findViewById(R.id.btnWalletFilterReward)

        txtWalletIdentity.text =
            getString(
                R.string.wallet_identity_with_role_value,
                globalId,
                EconomyRoleManager.displayName(
                    this,
                    EconomyRoleManager.classify(globalId).name
                )
            )

        btnWalletRefresh.setOnClickListener {
            renderWallet()
        }

        btnWalletTransfer.setOnClickListener {
            showTransferDialog()
        }

        btnWalletIssueVoucher.setOnClickListener {
            showIssueVoucherDialog()
        }

        btnWalletViewVouchers.setOnClickListener {
            startActivity(VoucherSheetActivity.createIntent(this, globalId))
        }

        btnWalletShowQr.setOnClickListener {
            showWalletQr()
        }

        btnWalletScanQr.setOnClickListener {
            startQrScannerWithPermission()
        }

        btnWalletFilterAll.setOnClickListener {
            setFilter(WalletFilter.ALL)
        }

        btnWalletFilterIncoming.setOnClickListener {
            setFilter(WalletFilter.INCOMING)
        }

        btnWalletFilterOutgoing.setOnClickListener {
            setFilter(WalletFilter.OUTGOING)
        }

        btnWalletFilterReward.setOnClickListener {
            setFilter(WalletFilter.REWARD)
        }

        renderWallet()
    }

    override fun onResume() {
        super.onResume()
        renderWallet()
    }

    private fun renderWallet() {
        lifecycleScope.launch {
            val balances =
                withContext(Dispatchers.IO) {
                    FirebaseRemoteSyncManager.refreshControlData(
                        this@WalletActivity,
                        setOf(globalId)
                    )
                    TokenManager.ensureWalletBootstrap(globalId)
                    Pair(
                        TokenManager.getWalletBalanceForGlobalId(globalId),
                        FirebaseRemoteSyncManager.cachedWalletBalance(this@WalletActivity, globalId)
                    )
                }
            val localBalance = balances.first
            val balance = balances.second ?: localBalance

            val transactions =
                withContext(Dispatchers.IO) {
                    TokenManager.getWalletTransactions(globalId, 20)
                }
            val bridgeDecision =
                withContext(Dispatchers.IO) {
                    InternetBridgePolicyManager.evaluate(this@WalletActivity, globalId)
                }
            val bridgePolicy =
                InternetBridgePolicyManager.current(this@WalletActivity)
            val voucherSummary =
                withContext(Dispatchers.IO) {
                    VoucherQrManager.summaryForIssuer(this@WalletActivity, globalId)
                }
            val pendingSyncCount =
                FirebaseEconomySyncManager.pendingCount(this@WalletActivity)
            val syncSummary =
                FirebaseEconomySyncManager.summary(this@WalletActivity)
            val serverRole =
                FirebaseRemoteSyncManager.cachedWalletOwnerClass(this@WalletActivity, globalId)
                    ?: EconomyRoleManager.classify(globalId).name
            val roleLabel =
                EconomyRoleManager.displayName(this@WalletActivity, serverRole)

            txtWalletBalance.text =
                if (balances.second != null) {
                    buildString {
                        append(getString(R.string.wallet_balance_value_server, balance, localBalance))
                        append('\n')
                        append(getString(R.string.wallet_role_value, roleLabel))
                        if (pendingSyncCount > 0) {
                            append('\n')
                            append(getString(R.string.wallet_sync_pending_value, pendingSyncCount))
                        }
                    }
                } else {
                    buildString {
                        append(getString(R.string.wallet_balance_value, balance))
                        append('\n')
                        append(getString(R.string.wallet_role_value, roleLabel))
                        if (pendingSyncCount > 0) {
                            append('\n')
                            append(getString(R.string.wallet_sync_pending_value, pendingSyncCount))
                        }
                    }
                }
            txtWalletInternetStatus.text =
                getString(
                    R.string.wallet_internet_status_value,
                    if (bridgeDecision.allowed) {
                        getString(R.string.wallet_internet_status_ready)
                    } else {
                        getString(R.string.wallet_internet_status_local_only)
                    },
                    bridgePolicy.minimumInternetAccessBalance,
                    bridgeDecision.walletBalance,
                    bridgeDecision.detail
                )
            txtWalletVoucherSummary.text =
                buildString {
                    append(
                        getString(
                            R.string.wallet_voucher_summary_value,
                            voucherSummary.totalCount,
                            voucherSummary.readyCount,
                            voucherSummary.usedCount,
                            voucherSummary.revokedCount,
                            voucherSummary.readyAmount,
                            voucherSummary.usedAmount,
                            voucherSummary.revokedAmount
                        )
                    )
                    append('\n')
                    append(
                        getString(
                            R.string.wallet_sync_summary_value,
                            syncSummary.pendingCount,
                            syncSummary.syncedCount,
                            syncSummary.conflictCount,
                            syncSummary.failedCount
                        )
                    )
                }

            val filteredTransactions =
                transactions.filter { matchesFilter(it) }

            txtWalletRecent.text =
                if (filteredTransactions.isEmpty()) {
                    getString(R.string.wallet_recent_empty)
                } else {
                    val formatter =
                        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                    filteredTransactions.joinToString("\n\n") { tx ->
                        buildString {
                            append(formatter.format(Date(tx.timestamp)))
                            append('\n')
                            append(
                                getString(
                                    R.string.wallet_transaction_value,
                                    tx.amount,
                                    tx.reason
                                )
                            )
                        }
                    }
                }

            updateFilterButtons()
        }
    }

    private fun showTransferDialog(prefilledTarget: String = "") {
        val targetInput =
            EditText(this).apply {
                hint = getString(R.string.wallet_transfer_target_hint)
                inputType = InputType.TYPE_CLASS_TEXT
                setText(prefilledTarget)
            }
        val amountInput =
            EditText(this).apply {
                hint = getString(R.string.wallet_transfer_amount_hint)
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }

        val container =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 12, 32, 0)
                addView(targetInput)
                addView(amountInput)
            }

        AlertDialog.Builder(this)
            .setTitle(R.string.wallet_transfer_title)
            .setView(container)
            .setPositiveButton(R.string.wallet_transfer_button) { _, _ ->
                val targetGlobalId =
                    GlobalContactDirectory.normalizeGlobalId(
                        targetInput.text?.toString().orEmpty()
                    )
                val amount =
                    amountInput.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0

                if (targetGlobalId.isBlank() || amount <= 0.0) {
                    Toast.makeText(
                        this,
                        getString(R.string.wallet_transfer_invalid),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    val ok =
                        withContext(Dispatchers.IO) {
                            TokenManager.transferBetweenWallets(
                                fromGlobalId = globalId,
                                toGlobalId = targetGlobalId,
                                amount = amount,
                                reason = "WALLET_TRANSFER"
                            )
                        }

                    Toast.makeText(
                        this@WalletActivity,
                        if (ok) {
                            getString(R.string.wallet_transfer_success, amount, targetGlobalId)
                        } else {
                            getString(R.string.wallet_transfer_failed)
                        },
                        Toast.LENGTH_SHORT
                    ).show()

                    renderWallet()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showIssueVoucherDialog() {
        val amountInput =
            EditText(this).apply {
                hint = getString(R.string.voucher_issue_amount_hint)
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText("10")
            }
        val quantityInput =
            EditText(this).apply {
                hint = getString(R.string.voucher_issue_quantity_hint)
                inputType = InputType.TYPE_CLASS_NUMBER
                setText("1")
            }
        val validDaysInput =
            EditText(this).apply {
                hint = getString(R.string.voucher_issue_valid_days_hint)
                inputType = InputType.TYPE_CLASS_NUMBER
                setText("30")
            }

        val container =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 12, 32, 0)
                addView(
                    LinearLayout(this@WalletActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        addView(
                            Button(this@WalletActivity).apply {
                                text = getString(R.string.voucher_preset_5)
                                layoutParams =
                                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                                setOnClickListener { amountInput.setText("5") }
                            }
                        )
                        addView(
                            Button(this@WalletActivity).apply {
                                text = getString(R.string.voucher_preset_10)
                                layoutParams =
                                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                                        marginStart = 8
                                    }
                                setOnClickListener { amountInput.setText("10") }
                            }
                        )
                    }
                )
                addView(
                    LinearLayout(this@WalletActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, 8, 0, 0)
                        addView(
                            Button(this@WalletActivity).apply {
                                text = getString(R.string.voucher_preset_25)
                                layoutParams =
                                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                                setOnClickListener { amountInput.setText("25") }
                            }
                        )
                        addView(
                            Button(this@WalletActivity).apply {
                                text = getString(R.string.voucher_preset_50)
                                layoutParams =
                                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                                        marginStart = 8
                                    }
                                setOnClickListener { amountInput.setText("50") }
                            }
                        )
                    }
                )
                addView(amountInput)
                addView(quantityInput)
                addView(validDaysInput)
            }

        AlertDialog.Builder(this)
            .setTitle(R.string.voucher_issue_title)
            .setView(container)
            .setPositiveButton(R.string.voucher_issue_button) { _, _ ->
                val amount =
                    amountInput.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0
                val quantity =
                    quantityInput.text?.toString()?.trim()?.toIntOrNull() ?: 0
                val validDays =
                    validDaysInput.text?.toString()?.trim()?.toIntOrNull() ?: 0

                if (amount <= 0.0 || quantity <= 0 || validDays <= 0) {
                    Toast.makeText(
                        this,
                        getString(R.string.voucher_issue_invalid),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    val result =
                        withContext(Dispatchers.IO) {
                            VoucherQrManager.issueVouchers(
                                this@WalletActivity,
                                globalId,
                                amount,
                                quantity,
                                validDays
                            )
                        }

                    Toast.makeText(
                        this@WalletActivity,
                        if (result.success) {
                            getString(
                                R.string.voucher_issue_success,
                                quantity,
                                amount
                            )
                        } else {
                            getString(R.string.voucher_issue_failed)
                        },
                        Toast.LENGTH_SHORT
                    ).show()

                    renderWallet()
                    if (result.success) {
                        startActivity(VoucherSheetActivity.createIntent(this@WalletActivity, globalId))
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showWalletQr() {
        val qrBitmap =
            buildWalletQrBitmap(globalId)
        if (qrBitmap == null) {
            Toast.makeText(
                this,
                getString(R.string.wallet_scan_invalid),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val imageView =
            ImageView(this).apply {
                setImageBitmap(qrBitmap)
                setBackgroundColor(Color.WHITE)
                adjustViewBounds = true
                setPadding(24, 24, 24, 24)
            }

        val container =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 12, 32, 0)
                addView(
                    TextView(this@WalletActivity).apply {
                        text = getString(R.string.wallet_qr_value)
                        setTextColor(Color.parseColor("#E8F1F8"))
                        textSize = 14f
                    }
                )
                addView(imageView)
                addView(
                    TextView(this@WalletActivity).apply {
                        text = globalId
                        setTextColor(Color.parseColor("#FFD54F"))
                        textSize = 14f
                        setPadding(0, 12, 0, 0)
                    }
                )
            }

        AlertDialog.Builder(this)
            .setTitle(R.string.wallet_qr_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun startQrScannerWithPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startQrScanner()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startQrScanner() {
        qrScannerLauncher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(getString(R.string.wallet_scan_qr))
                .setBeepEnabled(true)
                .setOrientationLocked(false)
        )
    }

    private fun buildWalletQrBitmap(targetGlobalId: String): Bitmap? {
        return try {
            val bitMatrix: BitMatrix =
                MultiFormatWriter().encode(
                    "$WALLET_QR_PREFIX$targetGlobalId",
                    BarcodeFormat.QR_CODE,
                    720,
                    720
                )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    pixels[y * width + x] =
                        if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseWalletQr(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith(WALLET_QR_PREFIX)) {
            return GlobalContactDirectory.normalizeGlobalId(
                trimmed.removePrefix(WALLET_QR_PREFIX)
            )
        }
        return if (trimmed.startsWith("GX-", ignoreCase = true)) {
            GlobalContactDirectory.normalizeGlobalId(trimmed)
        } else {
            ""
        }
    }

    private fun redeemVoucher(voucherCode: String) {
        lifecycleScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    VoucherQrManager.redeemVoucher(
                        this@WalletActivity,
                        voucherCode,
                        globalId
                    )
                }

            Toast.makeText(
                this@WalletActivity,
                when {
                    result.success ->
                        getString(R.string.voucher_redeem_success, result.amount)
                    result.alreadyUsed ->
                        getString(R.string.voucher_redeem_used)
                    result.expired ->
                        getString(R.string.voucher_redeem_expired)
                    result.revoked ->
                        getString(R.string.voucher_redeem_revoked)
                    else ->
                        getString(R.string.voucher_redeem_failed)
                },
                Toast.LENGTH_SHORT
            ).show()

            renderWallet()
        }
    }

    private fun setFilter(filter: WalletFilter) {
        activeFilter = filter
        renderWallet()
    }

    private fun matchesFilter(transaction: TokenTransaction): Boolean {
        return when (activeFilter) {
            WalletFilter.ALL -> true
            WalletFilter.INCOMING -> transaction.amount > 0.0
            WalletFilter.OUTGOING -> transaction.amount < 0.0
            WalletFilter.REWARD ->
                transaction.reason.contains("REWARD", ignoreCase = true) ||
                    transaction.reason.contains("GENESIS", ignoreCase = true)
        }
    }

    private fun updateFilterButtons() {
        btnWalletFilterAll.isEnabled = activeFilter != WalletFilter.ALL
        btnWalletFilterIncoming.isEnabled = activeFilter != WalletFilter.INCOMING
        btnWalletFilterOutgoing.isEnabled = activeFilter != WalletFilter.OUTGOING
        btnWalletFilterReward.isEnabled = activeFilter != WalletFilter.REWARD
    }
}
