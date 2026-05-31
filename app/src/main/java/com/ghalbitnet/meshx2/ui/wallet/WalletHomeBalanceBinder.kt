package com.ghalbitnet.meshx2.ui.wallet

import android.content.Context
import android.util.Log
import android.widget.TextView
import com.ghalbitnet.meshx2.token.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * PHASE 244B — Realtime Wallet Balance Binder.
 *
 * This helper keeps the Home Wallet Hero balance synchronized with the
 * local wallet ledger without forcing MainActivity to know storage details.
 *
 * Usage target:
 * WalletHomeBalanceBinder.bind(
 *     scope = lifecycleScope,
 *     context = applicationContext,
 *     balanceView = txtBalance,
 *     globalIdProvider = { txtGlobalIdentity.text.toString() }
 * )
 */
object WalletHomeBalanceBinder {
    private const val TAG = "GHALBIT-WALLET-HOME"
    private const val REFRESH_INTERVAL_MS = 2500L

    fun bind(
        scope: CoroutineScope,
        context: Context,
        balanceView: TextView,
        globalIdProvider: () -> String
    ): Job {
        return scope.launch(Dispatchers.IO) {
            var lastRendered = ""
            while (isActive) {
                val globalId = globalIdProvider().trim()
                val nextText = try {
                    if (globalId.isBlank() || globalId == "GX-UNKNOWN") {
                        "0.000 GHBT"
                    } else {
                        TokenManager.ensureWalletBootstrap(globalId)
                        val balance = TokenManager.getLocalWalletBalance(globalId)
                        formatBalance(balance)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to refresh home wallet balance", e)
                    lastRendered.ifBlank { "0.000 GHBT" }
                }

                if (nextText != lastRendered) {
                    lastRendered = nextText
                    withContext(Dispatchers.Main) {
                        if (balanceView.text.toString() != nextText) {
                            balanceView.text = nextText
                        }
                    }
                }
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    fun formatBalance(balance: Double): String {
        return String.format(Locale.US, "%,.3f GHBT", balance)
    }
}
