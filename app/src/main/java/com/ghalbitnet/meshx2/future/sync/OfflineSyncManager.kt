package com.ghalbitnet.meshx2.future.sync

/**
 * OFFLINE SYNC MANAGER
 *
 * Tahap sekarang:
 * - antrean sinkronisasi sederhana
 *
 * Masa depan:
 * - sync antar node
 * - sync blockchain
 * - sync chat offline
 * - kompresi data
 */
object OfflineSyncManager {

    private val queue = mutableListOf<SyncItem>()

    fun add(item: SyncItem) {
        queue.add(item)
    }

    fun getPending(): List<SyncItem> {
        return queue.toList()
    }

    fun markSynced(id: String) {
        queue.removeAll { it.id == id }
    }

    fun count(): Int {
        return queue.size
    }
}

data class SyncItem(
    val id: String,
    val type: String,
    val payload: String,
    val timestamp: Long = System.currentTimeMillis()
)
