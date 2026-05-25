package com.ghalbitnet.meshx2.vpn

import android.content.Context
import android.net.TrafficStats

object UsageDownloadMonitor {

    private const val PREFS_NAME = "ghalbit_usage_download_monitor"
    private const val KEY_BASELINE_RX = "baseline_rx"
    private const val KEY_LAST_POLLED_RX = "last_polled_rx"
    private const val KEY_BASELINE_TX = "baseline_tx"
    private const val KEY_LAST_POLLED_TX = "last_polled_tx"

    fun startSession(context: Context) {
        val currentRx = safeBytes(TrafficStats.getTotalRxBytes())
        val currentTx = safeBytes(TrafficStats.getTotalTxBytes())
        if (currentRx < 0L || currentTx < 0L) {
            VpnLogManager.warn(
                "DOWNLOAD_COUNTER_UNAVAILABLE",
                "TrafficStats total upload/download tidak tersedia saat baseline start."
            )
            reset(context)
            return
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_BASELINE_RX, currentRx)
            .putLong(KEY_LAST_POLLED_RX, currentRx)
            .putLong(KEY_BASELINE_TX, currentTx)
            .putLong(KEY_LAST_POLLED_TX, currentTx)
            .apply()
        VpnLogManager.info(
            "DOWNLOAD_BASELINE_SET",
            "baselineRxBytes=$currentRx baselineTxBytes=$currentTx"
        )
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_BASELINE_RX)
            .remove(KEY_LAST_POLLED_RX)
            .remove(KEY_BASELINE_TX)
            .remove(KEY_LAST_POLLED_TX)
            .apply()
    }

    fun poll(
        context: Context,
        nodeId: String,
        sessionId: String
    ) {
        val currentRx = safeBytes(TrafficStats.getTotalRxBytes())
        val currentTx = safeBytes(TrafficStats.getTotalTxBytes())
        if (currentRx < 0L || currentTx < 0L) {
            VpnLogManager.warn(
                "DOWNLOAD_COUNTER_UNAVAILABLE",
                "TrafficStats total upload/download tidak tersedia."
            )
            return
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val baselineRx = prefs.getLong(KEY_BASELINE_RX, -1L)
        val previousRx = prefs.getLong(KEY_LAST_POLLED_RX, -1L)
        val baselineTx = prefs.getLong(KEY_BASELINE_TX, -1L)
        val previousTx = prefs.getLong(KEY_LAST_POLLED_TX, -1L)
        if (baselineRx < 0L || previousRx < 0L || baselineTx < 0L || previousTx < 0L) {
            prefs.edit()
                .putLong(KEY_BASELINE_RX, currentRx)
                .putLong(KEY_LAST_POLLED_RX, currentRx)
                .putLong(KEY_BASELINE_TX, currentTx)
                .putLong(KEY_LAST_POLLED_TX, currentTx)
                .apply()
            VpnLogManager.info("DOWNLOAD_BASELINE_SET", "baselineRxBytes=$currentRx baselineTxBytes=$currentTx")
            return
        }
        if (currentRx < previousRx || currentTx < previousTx) {
            prefs.edit()
                .putLong(KEY_BASELINE_RX, currentRx)
                .putLong(KEY_LAST_POLLED_RX, currentRx)
                .putLong(KEY_BASELINE_TX, currentTx)
                .putLong(KEY_LAST_POLLED_TX, currentTx)
                .apply()
            VpnLogManager.info("DOWNLOAD_BASELINE_SET", "baselineRxBytes reset ke $currentRx baselineTxBytes reset ke $currentTx")
            return
        }
        prefs.edit()
            .putLong(KEY_LAST_POLLED_RX, currentRx)
            .putLong(KEY_LAST_POLLED_TX, currentTx)
            .apply()
        val deltaRx = currentRx - previousRx
        val deltaTx = currentTx - previousTx
        if (deltaRx <= 0L && deltaTx <= 0L) return
        UsageMeter.recordNetworkDeltas(
            nodeId = nodeId,
            sessionId = sessionId,
            uploadBytes = deltaTx.coerceAtLeast(0L),
            downloadBytes = deltaRx.coerceAtLeast(0L)
        )
    }

    private fun safeBytes(value: Long): Long {
        return if (value == TrafficStats.UNSUPPORTED.toLong()) -1L else value.coerceAtLeast(0L)
    }
}
