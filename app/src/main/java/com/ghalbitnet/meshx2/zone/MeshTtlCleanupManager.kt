package com.ghalbitnet.meshx2.zone

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.core.node.NodeStatusManager
import com.ghalbitnet.meshx2.network.AckTracker
import com.ghalbitnet.meshx2.routing.RouteDiscovery
import com.ghalbitnet.meshx2.routing.RouteTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object MeshTtlCleanupManager {
    private const val TAG = "GHALBIT-LEDGER"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var cleanupJob: Job? = null

    fun start(
        context: Context,
        intervalMs: Long = 60000L,
        nodeTtlMs: Long = 120000L,
        routeTtlMs: Long = 120000L,
        ackTtlMs: Long = 30000L
    ) {
        if (cleanupJob?.isActive == true) {
            return
        }

        cleanupJob = scope.launch {
            while (isActive) {
                runCleanup(
                    context = context,
                    nodeTtlMs = nodeTtlMs,
                    routeTtlMs = routeTtlMs,
                    ackTtlMs = ackTtlMs
                )
                delay(intervalMs)
            }
        }
        Log.d(TAG, "Started passive TTL cleanup")
    }

    fun stop() {
        cleanupJob?.cancel()
        cleanupJob = null
    }

    suspend fun runCleanup(
        context: Context,
        nodeTtlMs: Long = 120000L,
        routeTtlMs: Long = 120000L,
        ackTtlMs: Long = 30000L
    ) {
        val prunedNodes = NodeStatusManager.pruneStaleNodes(nodeTtlMs)
        val prunedRoutes = RouteTable.clearExpired(routeTtlMs)
        RouteDiscovery.clearExpiredRoutes(routeTtlMs)
        AckTracker.clearExpired(ackTtlMs)
        val prunedLedgerEntries = ZoneLedgerStore.removeExpired(context)

        Log.d(
            TAG,
            "TTL cleanup completed nodes=$prunedNodes routes=$prunedRoutes zoneEntries=$prunedLedgerEntries"
        )
    }
}
