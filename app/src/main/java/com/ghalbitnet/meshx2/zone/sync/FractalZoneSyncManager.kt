package com.ghalbitnet.meshx2.zone.sync

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.future.sync.OfflineMeshMemoryStore
import com.ghalbitnet.meshx2.zone.ZoneLedgerStore

class FractalZoneSyncManager(
    private val context: Context,
    private val regionalSyncBridge: RegionalSyncBridge? = null,
    private val globalLedgerBridge: GlobalLedgerBridge? = null
) : ZoneSyncManager {
    companion object {
        private const val TAG = "GHALBIT-ZONE"
    }

    override suspend fun syncLocalDevice(): ZoneSyncResult {
        val snapshot = OfflineMeshMemoryStore.loadSnapshot(context)
        val count = snapshot?.knownNodes?.size ?: 0
        Log.d(TAG, "Local-device sync review count=$count")
        return ZoneSyncResult(
            success = true,
            scope = "local-device",
            deltaEntries = count,
            message = "Local device snapshot reviewed incrementally"
        )
    }

    override suspend fun syncLocalZone(zoneId: String): ZoneSyncResult {
        val ledger = ZoneLedgerStore.getZoneLedger(context, zoneId)
        val delta = ZoneSyncDelta(
            scope = "local-zone",
            changedEntries = ledger.entries.size
        )
        regionalSyncBridge?.publishRegionalDelta(zoneId, delta)
        globalLedgerBridge?.publishGlobalDelta(zoneId, delta)
        Log.d(TAG, "Local-zone sync review zone=$zoneId entries=${ledger.entries.size}")
        return ZoneSyncResult(
            success = true,
            scope = "local-zone",
            deltaEntries = ledger.entries.size,
            message = "Zone ledger prepared for incremental sync"
        )
    }
}
