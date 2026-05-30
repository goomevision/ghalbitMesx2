package com.ghalbitnet.meshx2.reliability

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.ghalbitnet.meshx2.chat.ChatRetryMetadataRegistry
import com.ghalbitnet.meshx2.core.network.ConnectivityStatusDetector
import com.ghalbitnet.meshx2.core.node.NodeStatusManager
import com.ghalbitnet.meshx2.file.FileTransferManager
import com.ghalbitnet.meshx2.network.AckTracker
import com.ghalbitnet.meshx2.routing.RouteDiscoveryDiagnostics
import com.ghalbitnet.meshx2.routing.RouteTable

object ReliabilitySignalCollector {

    fun collect(context: Context): List<ReliabilitySignalSnapshot> {
        val connectivity =
            ConnectivityStatusDetector.snapshot(
                context,
                NodeStatusManager.getOnlineNodes()
            )
        val batteryPct = readBatteryPercent(context)
        return listOf(
            ReliabilitySignalSnapshot(
                ReliabilitySignalType.ONLINE_NODE_COUNT,
                NodeStatusManager.onlineCount().toString(),
                "observational"
            ),
            ReliabilitySignalSnapshot(
                ReliabilitySignalType.ACK_PENDING_COUNT,
                AckTracker.pendingCount().toString(),
                "observational"
            ),
            ReliabilitySignalSnapshot(
                ReliabilitySignalType.ACK_RECEIVED_COUNT,
                AckTracker.receivedCount().toString(),
                "observational"
            ),
            ReliabilitySignalSnapshot(
                ReliabilitySignalType.PENDING_TRANSFER_COUNT,
                "${FileTransferManager.pendingReceiveCount()} recv / ${if (FileTransferManager.isSendingTransfer()) 1 else 0} send",
                "observational"
            ),
            ReliabilitySignalSnapshot(
                ReliabilitySignalType.RETRY_METADATA_COUNT,
                ChatRetryMetadataRegistry.count().toString(),
                "observational"
            ),
            ReliabilitySignalSnapshot(
                ReliabilitySignalType.ROUTE_COUNT,
                RouteTable.allRoutes().size.toString(),
                "observational"
            ),
            ReliabilitySignalSnapshot(
                ReliabilitySignalType.ROUTE_REQUEST_COUNT,
                RouteDiscoveryDiagnostics.pendingRequestCount().toString(),
                "observational"
            ),
            ReliabilitySignalSnapshot(
                ReliabilitySignalType.CONNECTIVITY_SCOPE,
                connectivity.scope.name.lowercase(),
                "derived"
            ),
            ReliabilitySignalSnapshot(
                ReliabilitySignalType.HOTSPOT_HINT,
                connectivity.detail.name.lowercase(),
                "derived"
            ),
            ReliabilitySignalSnapshot(
                ReliabilitySignalType.BATTERY_HINT,
                batteryPct?.toString() ?: "unknown",
                if (batteryPct != null) "observational" else "unavailable"
            )
        )
    }

    fun report(context: Context): String =
        buildString {
            appendLine("RELIABILITY SIGNALS")
            appendLine("======================")
            collect(context).forEach { signal ->
                appendLine("${signal.type.name.lowercase()}: ${signal.value} [${signal.label}]")
            }
        }.trimEnd()

    private fun readBatteryPercent(context: Context): Int? {
        val intent =
            context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            ) ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) {
            return null
        }
        return (level * 100) / scale
    }
}
